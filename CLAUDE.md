# 项目:科研管理平台(scitech)

> 前身:`D:\纺专\横向项目\科技处\`(纵向统计系统,阶段0-4b,已停用待 tag `archive-v1-final` 归档只读)。本项目按 35 条决策重起:横向项目流程为首个业务模块,平台化架构支撑后续 6+ 模块。设计文档:**`科技处/docs/superpowers/specs/2026-10-05-横向项目流程与PDF产出-design.md`**(必读);执行计划:`科技处/docs/superpowers/plans/2026-10-07-横向平台-N1底座建库-N2PDF子系统.md`

## 概述
- **本期**:平台底座+横向项目全流程(填报→待签PDF→线下盖章→回扫→科技处确认)+PDF 子系统(LibreOffice 便携版+poi-tl+JODConverter)+归档
- 系统不代签不代章;纸面意见留空;流程留痕走 rp_hx_flow_record
- 模块化架构(决策#32):平台层(system/auth/template/backup/notify/attachment)与业务层(hx)分包,业务模块不得互相 import;表前缀 sys_/rp_{module}_;字典 dict_type 带模块前缀
- kind=1 纵向仅预留,纵向模块需求确认后独立立项(决策#31)

## 技术栈
Java 17 + Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + MySQL 8 + jjwt 0.12.6 + poi-tl 1.12.2 + JODConverter 4.4.7 + poi 5.2.5 + pdfbox;Vue 3 + Vite + Element Plus + Pinia

## 环境(均在 D 盘)
- JDK 17.0.20:`D:\dev\jdk-17`(JAVA_HOME);Maven 3.9.16:`D:\dev\maven`(阿里云镜像,仓库 D:\dev\maven-repo)
- MySQL 8.0.46:服务 MySQL80,root 密码 `Research@2026`,项目库 **scitech_db**(utf8mb4)
- npm 源 npmmirror(安装加 `--registry=https://registry.npmmirror.com`)
- 旧仓库 `D:\纺专\横向项目\科技处\` = 只读搬运源+文档源,**不修改**

## 启动方式
```bash
# 后端:命令行一律用 package+java -jar(见下方踩坑①)
cd sc_backend && D:/dev/maven/bin/mvn package -DskipTests -q && D:/dev/jdk-17/bin/java -jar target/sc-backend-0.1.0.jar
# 接口 http://localhost:8080/api(context-path=/api);dev 建号 admin/admin123、teacher/teacher123

# 前端
cd sc_frontend && npm run dev   # http://localhost:5173,代理 /api→8080

# prod 启动(全环境变量,零代码改动)
DB_URL='jdbc:mysql://...' DB_USER=.. DB_PASSWORD=.. SC_JWT_SECRET='≥32字符' SC_CORS_ORIGIN='https://...' java -jar sc-backend-0.1.0.jar --spring.profiles.active=prod
```

## 数据库
- 建表/字典初值走 `spring.sql.init`(schema.sql IF NOT EXISTS + data.sql 先删后插,幂等,每次启动执行;**encoding 已显式 UTF-8**,勿删)
- 14 表:sys_user/dept/dict/config/notify/template + rp_project(15列主表)/rp_hx_project/rp_hx_flow/rp_hx_flow_record/rp_member/rp_budget/rp_attachment/rp_funding_record
- 金额一律 DECIMAL(14,2) 元(决策#25);逻辑删除 deleted;状态迁移一律条件更新 WHERE status=旧值(决策#34-②)
- 快照导出位置:`docs/db/scitech_db_full.sql`(N5 时 mysqldump)

## ⚠️ 踩坑记录(本项目实测)
1. **`mvn spring-boot:run` 在本机中文路径下必挂**(fork 子进程 classpath 编码损坏→ClassNotFoundException,cmd 包装也救不了)。命令行一律 `mvn package -DskipTests + java -jar`;IDE 开发用 VS Code F5(不受影响)
2. **spring.sql.init 读脚本默认平台编码(GBK)**:UTF-8 脚本写入即乱码(EFBFFD 替换字符)。`spring.sql.init.encoding: UTF-8` 必须显式配置(已配,注意它在 spring: 块下,别插错层级)
3. **jjwt 弱密钥 fail-fast**:HMAC secret <32 字符直接启动失败(WeakKeyException 240bits)。上线清单:SC_JWT_SECRET 必须 ≥32 字符
4. el-input 原生 input 无 type attribute(仅 DOM property):浏览器自动化选择器用 `input[placeholder=...]` 而非 `input[type=text]`

## 会话安全(决策#35,开发期已全部落地)
token 带 uid claim;AuthInterceptor 每请求按 uid 查库(停用立即 401、role 取库中最新);secret 环境变量化;dev 建号/prod 零默认账号;连错5次锁10分钟;登录接口外全部鉴权。**上线只剩配置**:真 secret/HTTPS(Nginx 终止,8080 仅 127.0.0.1)/正式账号强密/内网收紧/CORS 改实际域名

## 进度
- [x] N1-Task1 后端骨架(pom/yml三件/启动类) 2026-10-07
- [x] N1-Task2 建库 14 表+字典 7 类 31 行(幂等验证 3 次启动) 2026-10-07
- [x] N1-Task3 common/config 搬运+CORS 配置化(sc.cors-origin) 2026-10-07
- [x] N1-Task4 system 五模块搬运(SysUserService 拆纯查询,登录归 auth) 2026-10-07
- [x] N1-Task5 JWT 会话改造:JwtUtilTest 3/3 + scripts/test_auth.py 4/4 2026-10-07
- [x] N1-Task6 前端骨架搬运(纵向页面全删,Login/Layout/Notify+角色守卫) 2026-10-07
- [x] N1-Task7 验收:prod 环境变量启动零默认账号✅ 停用即失效✅ 锁定✅ 前端登录✅ 2026-10-07
- [ ] N2 PDF 子系统(Task8-14):LibreOffice 便携版/RmbCapitalUtil/4 张模板/sys_template 接口/渲染管线/模板管理页/验收
- [ ] N3 教师端三流程 / N4 科技处端 / N5 整体验收(依赖 N1/N2 实际形态另行编写计划)

## 约定
- 所有回复、注释、文档中文;数据表 sys_/rp_ 前缀;仅按用户要求 commit(已授权逐 Task commit 模式)
- 验证策略(2026-09-03 沿用):业务逻辑脚本批量接口测试;UI 渲染+接口连通即止(截图两张),交互用户手动验收
- 每阶段可运行验收后进下一阶段;会话结束前更新本文件进度

## Git
本仓库为新项目独立仓库(main 分支)。上游设计文档在旧仓库 docs/superpowers/ 下,引用路径见文首
