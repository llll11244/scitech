#!/usr/bin/env bash
# ============================================================================
# Task29 演示包组装脚本:把整个平台打成可拷走、单机一键起的 dist-demo/
#
#   dist-demo/
#     sc-backend-0.1.0.jar   后端(仓库 sc_backend 构建)
#     libreoffice/           652MB 裁剪便携版(仓库既有,git-ignored)
#     jdk17/                 便携 JDK17(拷 D:\dev\jdk-17,剔 jmods/include)
#     mysql/                 官方 zip 解压精简版(bin 剔 debug/pdb/lib + share)
#     mysql/data/            演示库数据快照(本脚本现场初始化+经 API 种子后停机)
#     web/                   前端 npm run build 产物(由后端 ROOT 上下文直出)
#     upload/                模板(种子期上传)+ 演示项目渲染件/扫描件
#     backup/ my.ini start.bat stop.bat README-演示.md
#
# 用法:bash scripts/build_demo_package.sh [--skip-build]
#   --skip-build 跳过 jar 与前端构建(调试组装时用)
# 端口纪律:组装期 DB=3307 / 种子应用=18080 / LibreOffice=2002,均须空闲;
#   3306 本机 MySQL80 与 8080 不受影响(成品 start.bat 才用 8080)。
# 演示库账号沿用 dev 口径:admin/admin123、teacher/teacher123、teacher2/teacher2123
#   (种子期以 dev profile 首启由 DataInitializer 建号;演示运行走 prod,不再建号)。
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist-demo"
MVN="${MVN:-D:/dev/maven/bin/mvn}"
JDK_SRC="${JDK_SRC:-D:/dev/jdk-17}"
JAVA_BIN="$JDK_SRC/bin/java.exe"
MYSQL_ZIP="$ROOT/scripts/cache/mysql-8.0.46-winx64.zip"
DB_PORT=3307
APP_PORT=18080
DB_ROOT_PWD='demo-root-2026'
DB_URL_SEED="jdbc:mysql://127.0.0.1:$DB_PORT/scitech_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"

SKIP_BUILD=0
FILES_ONLY=0
case "${1:-}" in
  --skip-build) SKIP_BUILD=1 ;;
  --files-only) FILES_ONLY=1 ;;   # 仅重写 my.ini/start.bat/stop.bat/README(调试启动器)
esac

die() { echo "[组装失败] $*" >&2; exit 1; }
section() { echo; echo "==================== $* ===================="; }

# ---------------- 预检 ----------------
section "预检"
[ -d "$DIST" ] || die "dist-demo 不存在,--files-only 仅用于微调已有包"
port_listening() { netstat -ano | grep -E "[:.]$1[[:space:]]" | grep -q LISTENING; }
if [ "$FILES_ONLY" -eq 1 ]; then
  echo "--files-only:仅重写 my.ini/start.bat/stop.bat/README"
else
  [ -f "$MYSQL_ZIP" ] || die "MySQL zip 不存在:$MYSQL_ZIP"
  [ -x "$ROOT/libreoffice/program/soffice.exe" ] || die "仓库 libreoffice/program/soffice.exe 不存在"
  [ -d "$JDK_SRC/bin" ] || die "JDK 不存在:$JDK_SRC"
  command -v python >/dev/null || die "python 不可用"
  command -v curl >/dev/null || die "curl 不可用"
  command -v unzip >/dev/null || die "unzip 不可用"
  for p in "$DB_PORT" "$APP_PORT" 2002; do
    if port_listening "$p"; then
      die "端口 $p 被监听占用(需 3307/18080/2002 空闲;先停本机 dev 后端实例)"
    fi
  done
  echo "预检通过"
fi

# ---------------- 1. 构建 ----------------
if [ "$FILES_ONLY" -eq 1 ]; then
  :
elif [ "$SKIP_BUILD" -eq 0 ]; then
  section "构建后端 jar 与前端 dist"
  (cd "$ROOT/sc_backend" && "$MVN" package -DskipTests -q)
  (cd "$ROOT/sc_frontend" && npm run build --silent)
fi
JAR="$ROOT/sc_backend/target/sc-backend-0.1.0.jar"
[ -f "$JAR" ] || die "jar 不存在:$JAR(先去掉 --skip-build 跑一次构建)"
[ -f "$ROOT/sc_frontend/dist/index.html" ] || die "前端 dist/index.html 不存在"

if [ "$FILES_ONLY" -eq 0 ]; then
# ---------------- 2. 组装骨架 ----------------
section "组装目录(约 1.3GB,拷贝需数分钟)"
rm -rf "$DIST"
mkdir -p "$DIST/web" "$DIST/backup" "$DIST/upload/templates" "$DIST/mysql/data"
cp "$JAR" "$DIST/"

echo "拷贝 libreoffice(652MB)..."
cp -r "$ROOT/libreoffice" "$DIST/libreoffice"

echo "解压并精简 MySQL(bin 剔 debug/pdb/lib,share 仅留 english/charsets)..."
unzip -q "$MYSQL_ZIP" -d "$DIST/.mz"
mv "$DIST/.mz/mysql-8.0.46-winx64" "$DIST/.mysql-full"
rm -rf "$DIST/.mz"
mkdir -p "$DIST/mysql/bin" "$DIST/mysql/share"
(cd "$DIST/.mysql-full/bin" && for f in *; do
  case "$f" in *-debug.dll|*.pdb|*.lib|*.exe.manifest) ;; *) cp "$f" "$DIST/mysql/bin/" ;; esac
done)
cp -r "$DIST/.mysql-full/share/english" "$DIST/mysql/share/english"
cp -r "$DIST/.mysql-full/share/charsets" "$DIST/mysql/share/charsets"
# 首启组件表登记的 component_reference_cache(缺了每次启动报 Cannot load component)
mkdir -p "$DIST/mysql/lib/plugin"
cp "$DIST/.mysql-full/lib/plugin/component_reference_cache.dll" "$DIST/mysql/lib/plugin/"
rm -rf "$DIST/.mysql-full"

echo "拷贝 JDK17(剔 jmods/include)..."
cp -r "$JDK_SRC" "$DIST/jdk17"
rm -rf "$DIST/jdk17/jmods" "$DIST/jdk17/include"

echo "拷贝前端 dist -> web/ ..."
cp -r "$ROOT/sc_frontend/dist/." "$DIST/web/"
fi  # FILES_ONLY=0 组装骨架结束

# ---------------- 3. 写启动/停止/配置/说明文件 ----------------
section "写 start.bat / stop.bat / my.ini / README"

cat > "$DIST/my.ini" <<'EOF'
# scitech 演示包 MySQL 配置。
# 刻意不含任何路径(纯参数),basedir/datadir/lc-messages-dir 由 start.bat 以
# 包根绝对路径显式传入(规避中文/空格路径下 cwd 解析差异,整包拷走即用)。
[mysqld]
port=3307
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
default-time-zone=+08:00
max_allowed_packet=64M
innodb_buffer_pool_size=256M
EOF

# .bat 以 GBK + CRLF 落盘(cmd 解析硬要求 CRLF,LF 行尾会令括号块/标签整体错乱)
write_bat() {
  if command -v iconv >/dev/null 2>&1; then
    iconv -f UTF-8 -t GBK | sed 's/$/\r/' > "$1"
  else
    sed 's/$/\r/' > "$1"
  fi
}

write_bat "$DIST/start.bat" <<'EOF'
@echo off
chcp 936 >nul
setlocal EnableExtensions
cd /d "%~dp0"
rem ============================================================
rem scitech 科研管理平台 - 演示包一键启动
rem 流程:清理上次残留 -> 起 MySQL(3307) -> 起应用(8080) -> 开浏览器
rem 等待一律用 powershell Start-Sleep:不受 PATH 里同名 GNU 工具干扰
rem 详细说明见 README-演示.md
rem ============================================================

if not exist "mysql\data\ibdata1" (
  echo [错误] mysql\data 不存在,演示包不完整,无法启动。
  pause
  exit /b 1
)
rem 包根绝对路径(去尾反斜杠)——mysqld 路径参数全部显式传,不吃 cwd
set "PKG=%~dp0"
set "PKG=%PKG:~0,-1%"

rem 清理上次残留(停旧应用/孤立 soffice/停库;本包路径范围)
call stop.bat --from-start >nul 2>&1

set "DB_URL=jdbc:mysql://127.0.0.1:3307/scitech_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
set "DB_USER=root"
set "DB_PASSWORD=demo-root-2026"
set "SC_JWT_SECRET=scitech-demo-secret-2026-please-change-in-production"
set "SC_CORS_ORIGIN=http://localhost:8080"
set "SC_STATIC_DIR=./web"

echo [1/3] 启动 MySQL(端口 3307,仅本机回环)...
"mysql\bin\mysqladmin.exe" --no-defaults -h127.0.0.1 -P3307 -uroot -pdemo-root-2026 ping >nul 2>&1
if errorlevel 1 (
  start "scitech-mysql" /min cmd /c "mysql\bin\mysqld.exe --defaults-file="%PKG%\my.ini" --basedir="%PKG%\mysql" --datadir="%PKG%\mysql\data" --lc-messages-dir="%PKG%\mysql\share\english" --console >>mysql-run.log 2>&1"
)
set /a TRIES=0
:WAIT_MYSQL
"mysql\bin\mysqladmin.exe" --no-defaults --connect-timeout=5 -h127.0.0.1 -P3307 -uroot -pdemo-root-2026 ping >nul 2>&1
if not errorlevel 1 goto MYSQL_OK
set /a TRIES=TRIES+1
if %TRIES% GEQ 90 (
  echo [错误] MySQL 180 秒内未就绪,请查看包根 mysql-run.log 后重试。
  pause
  exit /b 1
)
powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul 2>&1
goto WAIT_MYSQL
:MYSQL_OK

echo [2/3] MySQL 就绪,启动应用(端口 8080,首次启动约 1 分钟)...
start "scitech-app" /min cmd /c "jdk17\bin\java.exe -Dscitech.instance=demo -jar sc-backend-0.1.0.jar --spring.profiles.active=prod --sc.mysqldump-path=./mysql/bin/mysqldump.exe --sc.upload-dir=./upload --sc.backup-dir=./backup --sc.office-home=./libreoffice >>app-run.log 2>&1"

set /a TRIES=0
:WAIT_APP
curl -s -o "%TEMP%\scitech_probe_body.txt" -w "%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"username\":\"__probe__\",\"password\":\"x\"}" http://localhost:8080/api/auth/login >"%TEMP%\scitech_probe.txt" 2>nul
set "CODE=000"
set /p CODE=<"%TEMP%\scitech_probe.txt"
if not "%CODE%"=="" if not "%CODE%"=="000" goto APP_OK
set /a TRIES=TRIES+1
if %TRIES% GEQ 120 (
  echo [错误] 应用 6 分钟内未就绪,请查看包根 app-run.log 后重试。
  pause
  exit /b 1
)
powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul 2>&1
goto WAIT_APP
:APP_OK

echo [3/3] 平台已就绪: http://localhost:8080
if /i not "%~1"=="nobrowser" start "" http://localhost:8080
echo 演示账号: admin/admin123(科技处) teacher/teacher123(教师)
echo 停止平台请运行 stop.bat,应用日志见包根 app-run.log
powershell -NoProfile -Command "Start-Sleep -Seconds 8" >nul 2>&1
EOF

write_bat "$DIST/stop.bat" <<'EOF'
@echo off
chcp 936 >nul
setlocal EnableExtensions
cd /d "%~dp0"
set "SC_PKGD=%~dp0"
rem ============================================================
rem 停止演示平台:应用 java(按 scitech.instance=demo 标记)-> 本包路径的
rem 孤立 LibreOffice -> MySQL(3307);不影响机器上其它 Java/数据库实例
rem (同机同时跑两份演示包不被支持:java 按标记全停)
rem ============================================================

echo [1/3] 停止应用 java ...
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"name='java.exe'\" | Where-Object { $_.CommandLine -like '*scitech.instance=demo*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Output ('  killed java ' + $_.ProcessId) }"

echo [2/3] 停止 LibreOffice 渲染进程(仅本包路径)...
powershell -NoProfile -Command "$p=$env:SC_PKGD; $f=$p -replace '\\','/'; Get-CimInstance Win32_Process -Filter \"name='soffice.bin' or name='soffice.exe'\" | Where-Object { $_.CommandLine -like ('*'+$p+'*') -or $_.CommandLine -like ('*'+$f+'*') } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Output ('  killed ' + $_.Name + ' ' + $_.ProcessId) }"

echo [3/3] 关闭 MySQL ...
"mysql\bin\mysqladmin.exe" --no-defaults --connect-timeout=10 -h127.0.0.1 -P3307 -uroot -pdemo-root-2026 shutdown >nul 2>&1
echo 已全部停止。
if /i not "%~1"=="--from-start" powershell -NoProfile -Command "Start-Sleep -Seconds 3" >nul 2>&1
EOF

printf '\xEF\xBB\xBF' > "$DIST/README-演示.md"   # BOM:Windows 记事本直开
cat >> "$DIST/README-演示.md" <<'EOF'
# 科研管理平台(scitech)演示包 - 单机一键

零安装演示:整包拷到任意 Windows 10(1803+)x64 目录,双击 `start.bat` 即起,
浏览器自动打开 http://localhost:8080。首次启动约 1 分钟(MySQL+应用+文档渲染组件冷启动)。

## 演示账号(密码为演示口径,勿上生产)

| 账号 | 密码 | 角色 | 可演示内容 |
|---|---|---|---|
| admin | admin123 | 科技处管理员 | 审批(通过/退回)、到账录入、项目台账、模板/字典/用户管理、备份 |
| teacher | teacher123 | 教师 | 登记新项目、变更、验收、扫描件上传、到账明细(只读) |
| teacher2 | teacher2123 | 教师(乙) | 越权隔离演示用 |

## 内置演示数据

- **项目A「高性能纺织智能检测装备研发」(已结题)**:登记→审批→合同→变更(经费调增+延期)→
  审批→验收→审批全流程走完,待签件/盖章扫描件/终版验收表+验收报告(docx 与 PDF)均可下载查看。
- **项目B「功能性针织面料数字化配色系统开发」(在研)**:已立项,到账 2 笔(12.8 万 + 9.6 万)。
- 建议演示路线:teacher 登录看项目列表/详情/到账 → teacher 新建登记项目提交(出待签 docx/PDF)
  → admin 登录审批中心处理 → 项目台账/到账管理 → 系统管理(模板/字典/用户/备份)。

## 启动 / 停止

- 启动:`start.bat`(可带参数 `nobrowser` 不自动开浏览器)。重复点会自动清理上次残留再启动。
- 停止:`stop.bat`(停应用、本包路径的 LibreOffice 渲染进程与 MySQL;不影响机器上其它 Java/数据库)。
- 运行日志:包根 `app-run.log`(应用)与 `mysql-run.log`(数据库),排障先看这两个文件。
- 同机同一时间只跑一份演示包(java 按演示标记停止,多副本会互相清掉)。

## 端口与依赖(全部自包含,不依赖机器上已装的 Java/MySQL)

| 端口 | 用途 |
|---|---|
| 8080 | 应用与前端页面(本机访问) |
| 3307 | 演示 MySQL(仅绑定 127.0.0.1,root 密码 demo-root-2026) |
| 2002 | LibreOffice 渲染进程池(本机,由应用托管) |

冲突处理:8080/3307 被其它程序占用时,改 `my.ini` 的 port 与 `start.bat` 里的 DB_URL 端号即可;
杀毒软件若拦截 `mysql/bin/mysqld.exe`、`libreoffice/program/soffice.bin`,请放行。

## 重置演示数据

推荐做法:保留演示包原始压缩文件,重新解压整包即恢复初始数据。
(不建议现场重建:删 `mysql/data` 后需 initialize-insecure、建库、设密、
dev profile 首启建号、重传模板、重跑种子,整链路见仓库 `scripts/build_demo_package.sh`。)

## 边界说明

- 演示包内 JWT 密钥、账号密码、MySQL root 均为演示口径,**不可直接用于生产部署**;
- 待签文件默认只展示 Word 交付(PDF 已双出落盘,开关 deliver_pdf_visible 见系统配置);
- 数据为组装期快照:重启不重置,但删除包目录后一切清零。
EOF

echo "启动文件写入完成"
if [ "$FILES_ONLY" -eq 1 ]; then
  echo "--files-only 完成(未动 mysql/data 与其余内容)"
  exit 0
fi

# ---------------- 4. 演示数据快照(现场起库+种子) ----------------
# 踩坑(本机实测):git-bash 后台派生的子进程,argv 含中文路径会被截断(掉成盘符根)、
# cwd 也会失效;仓库路径含中文,故种子期建 ASCII junction 中转,所有子进程一律
# 走 junction 的 Windows 反斜杠绝对路径,不依赖 cwd、argv 无中文。
STAGE='D:\scitech-demo-stage'
cmd //c rmdir "$STAGE" >/dev/null 2>&1 || true
WIN_DIST=$(cygpath -w "$DIST")
cmd //c mklink //J "$STAGE" "$WIN_DIST" >/dev/null || die "创建 junction 失败:$STAGE -> $WIN_DIST"
cleanup_junction() { cmd //c rmdir "$STAGE" >/dev/null 2>&1 || true; }
trap cleanup_junction EXIT
MYSQLD_S="$STAGE\\mysql\\bin\\mysqld.exe"
MYSQL_S="$STAGE\\mysql\\bin\\mysql.exe"
MYSQLADMIN_S="$STAGE\\mysql\\bin\\mysqladmin.exe"

section "初始化演示 MySQL data($DB_PORT)"
"$MYSQLD_S" --defaults-file="$STAGE\\my.ini" --basedir="$STAGE\\mysql" \
  --datadir="$STAGE\\mysql\\data" --lc-messages-dir="$STAGE\\mysql\\share\\english" \
  --initialize-insecure \
  || { cleanup_junction; die "mysqld --initialize-insecure 失败(看上方输出;data 目录必须为空)"; }

echo "启动临时 mysqld ..."
# --console = 单进程直跑(不加则 Windows mysqld 自带守护父子进程,停库后会自行重启占端口)
rm -f "$DIST/.mysql_pid"
timeout 60 powershell -NoProfile -Command "\$p = Start-Process -FilePath '$STAGE\\mysql\\bin\\mysqld.exe' -ArgumentList @('--defaults-file=$STAGE\\my.ini','--basedir=$STAGE\\mysql','--datadir=$STAGE\\mysql\\data','--lc-messages-dir=$STAGE\\mysql\\share\\english','--console') -WindowStyle Hidden -RedirectStandardOutput '$STAGE\\mysqld-seed.log' -RedirectStandardError '$STAGE\\mysqld-seed.err.log' -PassThru; \$p.Id | Out-File -Encoding ascii '$STAGE\\.mysql_pid'" || true
sleep 1
MYSQLD_PID=""
[ -f "$DIST/.mysql_pid" ] && MYSQLD_PID=$(tr -d ' \r\n' < "$DIST/.mysql_pid")
[ -n "$MYSQLD_PID" ] || { cleanup_junction; die "mysqld 启动失败(pid 文件未生成)"; }
echo "mysqld pid=$MYSQLD_PID"
for i in $(seq 1 60); do
  if "$MYSQLADMIN_S" --no-defaults --connect-timeout=5 -h127.0.0.1 -P$DB_PORT -uroot ping >/dev/null 2>&1; then break; fi
  sleep 2
done
"$MYSQLADMIN_S" --no-defaults --connect-timeout=5 -h127.0.0.1 -P$DB_PORT -uroot ping >/dev/null 2>&1 \
  || { powershell -NoProfile -Command "Stop-Process -Id $MYSQLD_PID -Force" 2>/dev/null || true; cleanup_junction; die "mysqld 未在 120 秒内就绪,日志:$DIST/mysqld-seed.err.log"; }

"$MYSQL_S" --no-defaults --connect-timeout=10 -h127.0.0.1 -P$DB_PORT -uroot -e \
  "CREATE DATABASE scitech_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   ALTER USER 'root'@'localhost' IDENTIFIED BY '$DB_ROOT_PWD'; FLUSH PRIVILEGES;" \
  || { cleanup_junction; die "建库/设密失败"; }
echo "演示库就绪(空库;建表由应用首启 spring.sql.init 完成)"

section "首启应用(dev 建号)并注入演示数据(应用端口 $APP_PORT)"
# java 亦不经 msys 直接后台派生(重定向/PID 映射实测不可靠),由 PowerShell
# Start-Process 启动:PID 写文件交接(不走 $( ) 命令替换管道),环境变量经 $env: 注入
rm -f "$DIST/.app_pid"
timeout 60 powershell -NoProfile -Command "\$env:SPRING_DATASOURCE_URL='$DB_URL_SEED'; \$env:SPRING_DATASOURCE_USERNAME='root'; \$env:SPRING_DATASOURCE_PASSWORD='$DB_ROOT_PWD'; \$env:SC_CORS_ORIGIN='http://localhost:$APP_PORT'; \$p = Start-Process -FilePath '$JAVA_BIN' -ArgumentList @('-jar','$STAGE\\sc-backend-0.1.0.jar','--spring.profiles.active=dev','--server.port=$APP_PORT','--sc.upload-dir=$STAGE\\upload','--sc.backup-dir=$STAGE\\backup','--sc.office-home=$STAGE\\libreoffice') -WindowStyle Hidden -RedirectStandardOutput '$STAGE\\app-seed.log' -RedirectStandardError '$STAGE\\app-seed.err.log' -PassThru; \$p.Id | Out-File -Encoding ascii '$STAGE\\.app_pid'" || true
sleep 1
APP_PID=""
[ -f "$DIST/.app_pid" ] && APP_PID=$(tr -d ' \r\n' < "$DIST/.app_pid")
[ -n "$APP_PID" ] || die "java 启动失败(pid 文件未生成)"
echo "java pid=$APP_PID,等待就绪 ..."
READY=0
for i in $(seq 1 90); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d '{"username":"__probe__","password":"x"}' "http://localhost:$APP_PORT/api/auth/login" 2>/dev/null || true)
  if [ -n "$CODE" ] && [ "$CODE" != "000" ]; then READY=1; break; fi
  sleep 2
done
[ "$READY" = 1 ] || { powershell -NoProfile -Command "Stop-Process -Id $APP_PID -Force" 2>/dev/null || true; die "应用未就绪,日志:$DIST/app-seed.log / app-seed.err.log"; }

# 建表由首启完成,院部种子在建表之后(登记表单依赖 deptId)
"$MYSQL_S" --no-defaults --connect-timeout=10 -h127.0.0.1 -P$DB_PORT -uroot -p"$DB_ROOT_PWD" --default-character-set=utf8mb4 \
  < "$ROOT/scripts/seed_dept_dev.sql" \
  || { powershell -NoProfile -Command "Stop-Process -Id $APP_PID -Force" 2>/dev/null || true; die "院部种子失败"; }

python "$ROOT/scripts/seed_demo_data.py" --base "http://localhost:$APP_PORT/api" \
  --templates "$ROOT/templates-src" \
  || { powershell -NoProfile -Command "Stop-Process -Id $APP_PID -Force" 2>/dev/null || true; die "种子脚本失败"; }

section "停机,固化数据快照"
powershell -NoProfile -Command "Stop-Process -Id $APP_PID -Force" 2>/dev/null || true
sleep 2
# 清理可能孤立的 soffice(仅本包路径;junction 与真实两形态都匹配)
powershell -NoProfile -Command "\$p='$STAGE'; \$d='$WIN_DIST'.Replace('/','\\'); Get-CimInstance Win32_Process -Filter \"name='soffice.bin' or name='soffice.exe'\" | Where-Object { \$_.CommandLine -like ('*'+\$p+'*') -or \$_.CommandLine -like ('*'+\$d+'*') } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force; Write-Output ('killed soffice ' + \$_.ProcessId) }" || true
for i in $(seq 1 30); do
  port_listening 2002 || break
  sleep 1
done
"$MYSQLADMIN_S" --no-defaults --connect-timeout=10 -h127.0.0.1 -P$DB_PORT -uroot -p"$DB_ROOT_PWD" shutdown \
  || { powershell -NoProfile -Command "Stop-Process -Id $MYSQLD_PID -Force" 2>/dev/null || true; die "mysqladmin shutdown 失败"; }
for i in $(seq 1 60); do
  port_listening "$DB_PORT" || break
  sleep 1
done
if port_listening "$DB_PORT"; then
  powershell -NoProfile -Command "Stop-Process -Id $MYSQLD_PID -Force" 2>/dev/null || true
  sleep 3
  port_listening "$DB_PORT" && die "mysqld 未退出"
fi

# 种子期日志/pid 文件清理(句柄可能延迟释放,容错重试,残留不影响包体)
sleep 3
for f in mysqld-seed.log mysqld-seed.err.log app-seed.log app-seed.err.log .mysql_pid .app_pid; do
  rm -f "$DIST/$f" 2>/dev/null || true
done
sleep 2
rm -f "$DIST"/mysqld-seed.log "$DIST"/mysqld-seed.err.log "$DIST"/app-seed.log \
  "$DIST"/app-seed.err.log "$DIST"/.mysql_pid "$DIST"/.app_pid 2>/dev/null || true
echo "数据快照固化完成:mysql/data(干净停机)"

# ---------------- 5. 摘要 ----------------
section "组装完成"
du -sm "$DIST" | awk '{printf "dist-demo 总体积: %.2f GB\n", $1/1024}'
du -sm "$DIST"/* 2>/dev/null | sort -rn | head -8 | awk '{printf "  %-40s %6.0f MB\n", $2, $1}'
echo
echo "验收:整包拷到另一目录(勿直接在 dist-demo 内启动),双击 start.bat"
echo "例如:robocopy \"$DIST\" D:/scitech-demo-test /E"
