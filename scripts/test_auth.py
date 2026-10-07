# -*- coding: utf-8 -*-
"""
N1 会话安全冒烟测试(决策#35):4 断言
  1. admin 正确登录 → code=0 且带 token
  2. 密码错误 → 提示"用户名或密码错误"
  3. 连错 5 次后第 6 次被锁定拒绝
  4. 停用账号后其 token 立即 401(拦截器每请求查库)
用法:先以 dev profile 启动应用(DataInitializer 建好 admin/teacher),再运行本脚本。
"""
import os
import subprocess
import sys

import requests

BASE = "http://localhost:8080/api"
MYSQL = ["mysql", "-uroot", "scitech_db", "-N", "-e"]

results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login(username, password):
    return requests.post(BASE + "/auth/login", json={"username": username, "password": password}, timeout=10)


def mysql_exec(sql):
    env = dict(os.environ, MYSQL_PWD="Research@2026")
    subprocess.run(MYSQL + [sql], env=env, check=True, capture_output=True)


def main():
    # 自建专用测试账号(复制 teacher 的 BCrypt hash,密码=teacher123):
    # 每次运行新建,不残留上一轮的内存锁定态,脚本幂等可重跑
    mysql_exec("DELETE FROM sys_user WHERE username='locktest'")
    mysql_exec("INSERT INTO sys_user (username, password, real_name, role, status, create_by) "
               "SELECT 'locktest', password, '锁定测试', 'teacher', 1, 'test' FROM sys_user WHERE username='teacher'")

    # 断言1:正确登录
    r = login("admin", "admin123").json()
    check("登录成功返回token", r.get("code") == 0 and r.get("data", {}).get("token"))

    # 断言2:密码错误
    r = login("locktest", "wrong-password").json()
    check("密码错误统一提示", r.get("code") != 0 and "用户名或密码错误" in r.get("msg", ""), r.get("msg", ""))

    # 断言3:连错5次后第6次锁定(上面已错1次,再错4次凑满5次)
    for _ in range(4):
        login("locktest", "wrong-password")
    r = login("locktest", "wrong-password").json()
    check("第6次尝试被锁定", r.get("code") != 0 and "锁定" in r.get("msg", ""), r.get("msg", ""))

    token = login("admin", "admin123").json()["data"]["token"]

    # 断言4:停用 admin 后其 token 立即失效(拦截器查库带出最新状态)
    headers = {"Authorization": "Bearer " + token}
    before = requests.get(BASE + "/dicts?type=hx_project_type", headers=headers, timeout=10)
    mysql_exec("UPDATE sys_user SET status=0 WHERE username='admin'")
    after = requests.get(BASE + "/dicts?type=hx_project_type", headers=headers, timeout=10)
    mysql_exec("UPDATE sys_user SET status=1 WHERE username='admin'")  # 恢复
    check("停用后token立即401", before.status_code == 200 and after.status_code == 401,
          "before=%s after=%s" % (before.status_code, after.status_code))

    failed = [n for n, ok in results if not ok]
    mysql_exec("DELETE FROM sys_user WHERE username='locktest'")  # 清理测试账号
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
