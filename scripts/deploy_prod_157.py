# -*- coding: utf-8 -*-
"""43.136.168.157 生产部署(Ubuntu · 客户测试环境 · HTTP 版)。

用法:
  python scripts/deploy_prod_157.py install   # 阶段1:apt 装 JDK17/MySQL/nginx/fail2ban + 2G swap + 时区(慢)
  python scripts/deploy_prod_157.py setup     # 阶段2:建库/目录/上传包/环境文件/systemd/nginx/ufw/启动/引导admin
  python scripts/deploy_prod_157.py status    # 日常巡检:服务/端口/资源/最近日志
  python scripts/deploy_prod_157.py verify    # 本机侧验证:公网 80 首页/手册/登录

安全口径(部署时注意系统安全的落地):
  · 公网仅开 22(SSH)/80(HTTP 经 nginx);8080/3306 仅本机回环,ufw 默认拒绝入站
  · MySQL 应用账号 scitech@localhost 随机强密;root 走 auth_socket;bind 127.0.0.1
  · SC_JWT_SECRET 随机 48B;凭据全部落 /opt/scitech/app/scitech.env(root:600)
  · 服务专用低权系统用户 scitech(nologin),WorkingDirectory=/opt/scitech
  · fail2ban 防 SSH 爆破;应用自身连错 5 次锁 10 分钟
  · admin 引导:prod 无 DataInitializer,首启后 SQL 插 BCrypt(Kjc@2026)
幂等:两阶段均可重跑;scitech.env 已存在则沿用旧凭据(不破坏在跑服务)。
"""
import os
import secrets
import sys
import time

import paramiko

# ---- 凭据口径:SSH 密码不进库。优先环境变量 SC_SSH_PWD,否则读同目录
# scripts/deploy_secrets.py(git-ignored,内容:SSH_PWD="...")。两者都缺则拒绝运行。
HOST = os.environ.get("SC_HOST", "43.136.168.157")
USER = os.environ.get("SC_SSH_USER", "ubuntu")
PWD = os.environ.get("SC_SSH_PWD")
if not PWD:
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        from deploy_secrets import SSH_PWD  # noqa: E402
        PWD = SSH_PWD
    except ImportError:
        pass
if not PWD:
    raise SystemExit("缺少 SSH 密码:设 SC_SSH_PWD 环境变量或创建 scripts/deploy_secrets.py(见脚本头注释)")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "sc_backend", "target", "sc-backend-0.1.0.jar")
DIST = os.path.join(ROOT, "sc_frontend", "dist")
MANUAL = os.path.join(ROOT, "docs", "横向项目使用手册.html")
ADMIN_PWD = "Kjc@2026"
ENV_FILE = "/opt/scitech/app/scitech.env"
DB_URL = ("jdbc:mysql://127.0.0.1:3306/scitech_db?useUnicode=true"
          "&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true")

NGINX_CONF = """server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    client_max_body_size 60m;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }
}
"""

SYSTEMD = """[Unit]
Description=Scitech sc-backend (prod)
After=network.target mysql.service
Wants=mysql.service

[Service]
User=scitech
WorkingDirectory=/opt/scitech
EnvironmentFile=/opt/scitech/app/scitech.env
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/scitech/app/sc-backend.jar --spring.profiles.active=prod --sc.mysqldump-path=/usr/bin/mysqldump
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
"""


def connect():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PWD, timeout=20)
    return c


def run(c, cmd, timeout=150, check=True, quiet=False):
    if not quiet:
        print("$", cmd)
    _, out, err = c.exec_command(cmd, timeout=timeout)
    rc = out.channel.recv_exit_status()
    o = out.read().decode(errors="replace").strip()
    e = err.read().decode(errors="replace").strip()
    if not quiet:
        head = (o[:600] + ("…" if len(o) > 600 else "")) if o else ""
        tail = ("  [stderr] " + e[:300]) if e else ""
        if head or tail or rc != 0:
            print(f"  rc={rc} {head}{tail}")
    if check and rc != 0:
        raise SystemExit(f"命令失败(rc={rc}):{cmd}\n{e[:800]}")
    return o


def put(c, local, remote):
    sftp = c.open_sftp()
    sftp.put(local, remote)
    st = sftp.stat(remote)
    sftp.close()
    print(f"  upload {os.path.basename(local)} -> {remote} ({st.st_size/1024/1024:.1f} MB)")


def phase_install(c):
    print("== 阶段1:系统软件安装 ==")
    run(c, "sudo apt-get update -y -o Acquire::Retries=3", timeout=300)
    run(c, "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "
           "openjdk-17-jre-headless mysql-server nginx fail2ban", timeout=540)
    print(run(c, "java -version 2>&1 | head -1"))
    run(c, "systemctl is-active mysql nginx")
    # swap(2G 小内存机器防 OOM;已存在则跳过)
    if not run(c, "swapon --show | grep -c .", quiet=True):
        run(c, "sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && "
               "sudo mkswap /swapfile && sudo swapon /swapfile")
        run(c, "grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab")
    run(c, "sudo timedatectl set-timezone Asia/Shanghai")
    print("阶段1 完成")


def phase_setup(c):
    print("== 阶段2:应用部署 ==")
    # ---- 凭据:已存在则沿用 ----
    env_exist = run(c, f"sudo test -f {ENV_FILE} && echo yes || echo no", quiet=True) == "yes"
    if env_exist:
        old = run(c, f"sudo cat {ENV_FILE}", quiet=True)
        env = {}
        for line in old.splitlines():
            if "=" in line:
                k, v = line.split("=", 1)
                env[k] = v
        print("  沿用已有 scitech.env(DB_USER=%s)" % env.get("DB_USER"))
    else:
        env = {
            "DB_URL": DB_URL,
            "DB_USER": "scitech",
            "DB_PASSWORD": "Sc" + secrets.token_urlsafe(15),
            "SC_JWT_SECRET": secrets.token_urlsafe(48),
            "SC_CORS_ORIGIN": f"http://{HOST}",
            "SC_STATIC_DIR": "/opt/scitech/web",
        }
    env.setdefault("DB_URL", DB_URL)
    env.setdefault("DB_USER", "scitech")
    env.setdefault("SC_CORS_ORIGIN", f"http://{HOST}")
    env.setdefault("SC_STATIC_DIR", "/opt/scitech/web")
    env_body = "".join(f"{k}={v}\n" for k, v in env.items())

    # ---- MySQL:库+应用账号(localhost only) ----
    dbpwd = env["DB_PASSWORD"]
    run(c, "sudo mysql -e \"CREATE DATABASE IF NOT EXISTS scitech_db CHARACTER SET utf8mb4 "
           "COLLATE utf8mb4_unicode_ci;\"")
    run(c, f"sudo mysql -e \"CREATE USER IF NOT EXISTS 'scitech'@'localhost' IDENTIFIED BY '{dbpwd}'; "
           f"ALTER USER 'scitech'@'localhost' IDENTIFIED BY '{dbpwd}'; "
           f"GRANT ALL PRIVILEGES ON scitech_db.* TO 'scitech'@'localhost'; FLUSH PRIVILEGES;\"")
    bind = run(c, "ss -tln | grep 3306 || true", quiet=True)
    assert "127.0.0.1:3306" in bind, f"MySQL 未绑回环:{bind}"

    # ---- 目录与服务用户 ----
    run(c, "id scitech >/dev/null 2>&1 || sudo useradd -r -s /usr/sbin/nologin scitech")
    run(c, "sudo mkdir -p /opt/scitech/app /opt/scitech/web /opt/scitech/upload /opt/scitech/backup")
    run(c, "sudo rm -rf /tmp/sc-deploy && mkdir -p /tmp/sc-deploy")

    # ---- 上传:jar + 前端 dist(打 tar)+ 手册(本地打包用 tarfile,中文路径不进 bash 参数) ----
    import tarfile
    tgz_local = os.path.join(ROOT, "sc_frontend", "dist.tgz")
    if os.path.exists(tgz_local):
        os.remove(tgz_local)
    with tarfile.open(tgz_local, "w:gz") as tf:
        tf.add(DIST, arcname=".")
    put(c, JAR, "/tmp/sc-deploy/sc-backend.jar")
    put(c, tgz_local, "/tmp/sc-deploy/dist.tgz")
    put(c, MANUAL, "/tmp/sc-deploy/manual.html")

    run(c, "sudo systemctl stop scitech 2>/dev/null; true", check=False)
    run(c, "sudo install -m 644 /tmp/sc-deploy/sc-backend.jar /opt/scitech/app/sc-backend.jar")
    run(c, "sudo rm -rf /opt/scitech/web/* && sudo tar -xzf /tmp/sc-deploy/dist.tgz -C /opt/scitech/web")
    run(c, "sudo install -m 644 /tmp/sc-deploy/manual.html /opt/scitech/web/manual.html")
    run(c, "sudo chown -R scitech:scitech /opt/scitech")

    # ---- 环境文件(root:600)+ systemd ----
    import tempfile
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, suffix=".env") as f:
        f.write(env_body)
        env_local = f.name
    put(c, env_local, "/tmp/sc-deploy/scitech.env")
    os.unlink(env_local)
    run(c, f"sudo install -m 600 -o root -g root /tmp/sc-deploy/scitech.env {ENV_FILE}")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as f:
        f.write(SYSTEMD)
        unit_local = f.name
    put(c, unit_local, "/tmp/sc-deploy/scitech.service")
    os.unlink(unit_local)
    run(c, "sudo install -m 644 /tmp/sc-deploy/scitech.service /etc/systemd/system/scitech.service")
    run(c, "sudo systemctl daemon-reload && sudo systemctl enable scitech >/dev/null && sudo systemctl start scitech")

    # ---- 等应用就绪(schema/data.sql 建表种子) ----
    ready = False
    for _ in range(30):
        code = run(c, "curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "
                      "-d '{}' http://127.0.0.1:8080/api/auth/login", quiet=True, check=False)
        if code in ("200", "400", "405", "500"):
            ready = True
            break
        time.sleep(3)
    if not ready:
        print(run(c, "sudo journalctl -u scitech -n 40 --no-pager", check=False))
        raise SystemExit("应用 90 秒内未就绪,看上方日志")
    print("  应用已就绪(8080 本机回环)")

    # ---- 引导 admin(prod 无默认账号;已存在不动) ----
    import bcrypt
    h = bcrypt.hashpw(ADMIN_PWD.encode(), bcrypt.gensalt()).decode()
    sql = ("INSERT INTO sys_user (username,password,real_name,role,status,create_by) "
           f"SELECT 'admin','{h}','系统管理员','admin',1,'system' "
           "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='admin');")
    import tempfile as tf
    with tf.NamedTemporaryFile("w", encoding="utf-8", delete=False, suffix=".sql") as f:
        f.write(sql)
        sql_local = f.name
    put(c, sql_local, "/tmp/sc-deploy/admin.sql")
    os.unlink(sql_local)
    run(c, "sudo mysql --default-character-set=utf8mb4 scitech_db < /tmp/sc-deploy/admin.sql")
    n = run(c, "sudo mysql --default-character-set=utf8mb4 -N scitech_db -e \"SELECT COUNT(*) FROM sys_user;\"", quiet=True)
    print(f"  引导完成,sys_user 现有 {n} 个账号")

    # ---- nginx 反代(备份原 default) ----
    run(c, "sudo test -f /etc/nginx/sites-available/default.bak || "
           "sudo cp /etc/nginx/sites-available/default /etc/nginx/sites-available/default.bak")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as f:
        f.write(NGINX_CONF)
        ng_local = f.name
    put(c, ng_local, "/tmp/sc-deploy/nginx-default")
    os.unlink(ng_local)
    run(c, "sudo install -m 644 /tmp/sc-deploy/nginx-default /etc/nginx/sites-available/default")
    run(c, "sudo nginx -t && sudo systemctl reload nginx")

    # ---- ufw:默认拒绝,仅放行 22/80;fail2ban ----
    run(c, "sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw --force enable")
    run(c, "sudo systemctl enable --now fail2ban")
    print(run(c, "sudo ufw status | head -10"))

    # ---- 服务器侧验证 ----
    for path in ("/", "/manual.html", "/api/auth/login"):
        method = "POST" if path.startswith("/api") else "GET"
        code = run(c, "curl -s -o /dev/null -w '%{http_code}' -X " + method + " http://127.0.0.1" + path,
                   quiet=True, check=False)
        print(f"  本机 curl {path} -> {code}")
    run(c, "rm -rf /tmp/sc-deploy")
    print("阶段2 完成")


def phase_status(c):
    print(run(c, "systemctl is-active scitech nginx mysql fail2ban; "
                 "sudo ufw status | head -6; ss -tln | grep -E ':(22|80|3306|8080) '; "
                 "df -h / | tail -1; free -m | head -2; uptime -p"))
    print(run(c, "sudo journalctl -u scitech -n 15 --no-pager | tail -15", check=False))


def phase_verify():
    import urllib.request
    import json
    print("== 本机公网验证 ==")
    for path, method in [("/", "GET"), ("/manual.html", "GET"), ("/api/auth/login", "POST")]:
        req = urllib.request.Request(f"http://{HOST}{path}", method=method,
                                     data=b"{}" if method == "POST" else None,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                print(f"  {path} -> {r.status}")
        except urllib.error.HTTPError as e:
            print(f"  {path} -> {e.code}(HTTP 服务可达)")
        except Exception as e:
            print(f"  {path} -> 不可达:{e}(若 22 能连而 80 不通,是云安全组未放行 80)")
    body = json.dumps({"username": "admin", "password": ADMIN_PWD}).encode()
    req = urllib.request.Request(f"http://{HOST}/api/auth/login", data=body,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=15) as r:
        d = json.loads(r.read().decode())
        print("  admin/Kjc@2026 公网登录:", "OK" if d.get("code") == 0 else d)


if __name__ == "__main__":
    phase = sys.argv[1] if len(sys.argv) > 1 else "status"
    if phase == "verify":
        phase_verify()
    else:
        cli = connect()
        try:
            {"install": phase_install, "setup": phase_setup, "status": phase_status}[phase](cli)
        finally:
            cli.close()
