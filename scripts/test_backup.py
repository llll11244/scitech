# -*- coding: utf-8 -*-
"""
备份子系统接口测试(计划 Task 27):8 断言
  1. GET /backup/status:available=true + cron/keep 就位(启动自检通过)
  2. POST /backup/run → started=true(异步,立即返回)
  3. 连发第二次 run → started=false(进行中语义,同一同步方法锁防并发)
  4. 轮询 /backup/list 出现新 zip 且大小>1KB
  5. 下载该 zip:前 2 字节 == b"PK"
  6. DELETE 后列表不再出现
  7. teacher 拒 list/run(仅 admin)
  8. download name=../data.sql 拒(路径遍历防护)
前置:应用(dev)已启动,sc.mysqldump-path 指向本机可用 mysqldump
清理:删除测试期间新生成的备份 zip(保留 backup/ 目录与既有文件)
"""
import sys
import time

import requests

BASE = "http://localhost:8080/api"

results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login(username, password):
    # 2026-10-24 测试教师密码直改库为 123;兼容旧口径 teacher123 回退
    for pwd in (password, "123", username + "123"):
        r = requests.post(BASE + "/auth/login", json={"username": username, "password": pwd},
                          timeout=10).json()
        if r.get("code") == 0:
            return {"Authorization": "Bearer " + r["data"]["token"]}
    raise SystemExit("登录失败:" + username)


def main():
    admin = login("admin", "admin123")
    teacher = login("teacher", "teacher123")

    # 1. 启动自检状态
    r = requests.get(BASE + "/backup/status", headers=admin, timeout=10).json()
    ok = (r.get("code") == 0 and r["data"].get("available") is True
          and bool(r["data"].get("cron")) and r["data"].get("keep") == 8)
    check("status 自检可用+cron/keep", ok, str(r)[:100])

    # 7. teacher 拒(仅 admin)
    r1 = requests.get(BASE + "/backup/list", headers=teacher, timeout=10).json()
    r2 = requests.post(BASE + "/backup/run", headers=teacher, timeout=10).json()
    check("teacher 拒 list/run", r1.get("code") != 0 and r2.get("code") != 0
          and "管理员" in (r1.get("msg") or ""), str(r1.get("msg"))[:60])

    # 8. 路径遍历拒
    r = requests.get(BASE + "/backup/download", headers=admin,
                     params={"name": "../data.sql"}, timeout=10).json()
    check("download 路径遍历拒", r.get("code") != 0, str(r.get("msg"))[:60])

    # 备份前既有文件(测试只清理自己新增的)
    pre = {f["name"] for f in (requests.get(BASE + "/backup/list", headers=admin,
                                            timeout=10).json()["data"] or [])}

    # 2. 手动触发(异步立即返回)+ 3. 连发第二次=进行中
    r = requests.post(BASE + "/backup/run", headers=admin, timeout=10).json()
    ok_run = r.get("code") == 0 and r["data"]["started"] is True
    r2 = requests.post(BASE + "/backup/run", headers=admin, timeout=10).json()
    ok_conc = r2.get("code") == 0 and r2["data"]["started"] is False
    check("run 立即返回 started=true", ok_run, str(r)[:80])
    check("并发第二次 run=进行中语义", ok_conc, str(r2)[:80])

    # 4. 轮询新 zip(全库 SQL+upload 打包,>1KB)
    new_file = None
    deadline = time.time() + 180
    while time.time() < deadline:
        files = (requests.get(BASE + "/backup/list", headers=admin, timeout=10).json()["data"] or [])
        fresh = [f for f in files if f["name"] not in pre]
        if fresh:
            new_file = fresh[0]
            if new_file["size"] > 1024:
                break
        time.sleep(3)
    check("轮询出新 zip(>1KB)", new_file is not None and new_file["size"] > 1024,
          str(new_file)[:100])

    # 5. 下载校验 zip 魔数
    dl_ok = False
    if new_file:
        resp = requests.get(BASE + "/backup/download", headers=admin,
                            params={"name": new_file["name"]}, timeout=120)
        dl_ok = resp.status_code == 200 and resp.content[:2] == b"PK"
    check("download 字节=zip 魔数 PK", dl_ok)

    # 6. 删除后列表消失
    if new_file:
        requests.delete(BASE + "/backup", headers=admin, params={"name": new_file["name"]}, timeout=10)
    files = (requests.get(BASE + "/backup/list", headers=admin, timeout=10).json()["data"] or [])
    names = {f["name"] for f in files}
    gone = new_file is None or new_file["name"] not in names
    check("delete 后列表消失", gone)

    # 清理:删除测试新增的其余备份 zip(如并发检查触发过第二轮),保留既有
    for name in names - pre:
        requests.delete(BASE + "/backup", headers=admin, params={"name": name}, timeout=10)

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
