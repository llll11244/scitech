# -*- coding: utf-8 -*-
"""客户测试环境账号准备(2026-11-08):
8 套教师账号(工号 210001~210008,覆盖 8 个学院、职称梯度、真实感姓名)
+ admin2(科技处管理端二号)。原有 admin/admin123、teacher/teacher2 保留不动。

口径:建号走 admin 接口(临时强密 Init2026,过接口强度校验 8~32 位含字母数字)
→ 各自登录走自助改密通道 PUT /users/password 改为客户口径 123321
(自助通道 6~32 位无字符集限制,全流程走真实代码路径,不碰库改密文)。
学位(degree)不在建号接口字段内,建号后另行 SQL 补(见脚本尾部打印提示)。

幂等:用户名已存在则跳过建号;临时密登不上会试 123321(已准备过的号)再验证。
用法:PYTHONIOENCODING=utf-8 python scripts/prepare_customer_accounts.py
前置:后端已启动(dev),admin/admin123 可登,业务库已清空。
"""
import sys
import time

import requests

BASE = "http://localhost:8080/api"
ADMIN_PWD = "admin123"
# 可选参数:--base http://43.136.168.157/api --admin-pwd Kjc@2026(部署服务器建号用)
_args = sys.argv[1:]
for _i, _a in enumerate(_args):
    if _a == "--base" and _i + 1 < len(_args):
        BASE = _args[_i + 1]
    if _a == "--admin-pwd" and _i + 1 < len(_args):
        ADMIN_PWD = _args[_i + 1]
TEMP_PWD, FINAL_PWD = "Init2026", "123321"

# (登录名/工号, 姓名, dept_id, 院部, 职称, 学位, 手机, 角色)
ACCOUNTS = [
    ("210001", "王志远", 9,  "智能制造与装备学院", "教授",   "博士", "13800001001", "teacher"),
    ("210002", "李慧敏", 10, "纺织与材料工程学院", "副教授", "硕士", "13800001002", "teacher"),
    ("210003", "张建华", 11, "信息工程学院",       "副教授", "博士", "13800001003", "teacher"),
    ("210004", "刘晓燕", 12, "经济管理学院",       "讲师",   "硕士", "13800001004", "teacher"),
    ("210005", "陈国强", 13, "数字经济学院",       "讲师",   "博士", "13800001005", "teacher"),
    ("210006", "杨丽娟", 14, "建筑工程学院",       "助教",   "硕士", "13800001006", "teacher"),
    ("210007", "赵文博", 15, "艺术设计学院",       "教授",   "博士", "13800001007", "teacher"),
    ("210008", "周雪梅", 16, "外语与文化旅游学院", "助教",   "硕士", "13800001008", "teacher"),
    ("admin2", "科技处管理员", 19, "科技处", None, None, "13800001000", "admin"),
]

results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login(u, p):
    r = requests.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=10).json()
    return r["data"]["token"] if r.get("code") == 0 else None


def wait_backend(timeout_s=180):
    """连接拒绝→等;任何 HTTP 响应(含 400)即在线"""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            requests.post(BASE + "/auth/login", json={}, timeout=3)
        except requests.exceptions.ConnectionError:
            time.sleep(3)
        except Exception:
            return True
        return True
    return False


def main():
    if not wait_backend():
        raise SystemExit("后端 3 分钟内未就绪(8080 连接拒绝)")
    admin_tok = login("admin", ADMIN_PWD)
    if not admin_tok:
        raise SystemExit("admin/admin123 登录失败")
    h_admin = {"Authorization": "Bearer " + admin_tok}

    for u, name, dept, dept_name, title, degree, phone, role in ACCOUNTS:
        body = {"username": u, "password": TEMP_PWD, "realName": name,
                "role": role, "deptId": dept, "phone": phone}
        if title:
            body["title"] = title
        r = requests.post(BASE + "/users", headers=h_admin, json=body, timeout=10).json()
        if r.get("code") == 0:
            check(f"建号 {u}({name},{dept_name},{role})", True)
        elif "已存在" in str(r.get("msg", "")):
            check(f"建号 {u}({name})", True, "(已存在,跳过)")
        else:
            check(f"建号 {u}({name})", False, str(r.get("msg")))
            continue

        tok = login(u, TEMP_PWD)
        if tok:
            r2 = requests.put(BASE + "/users/password",
                              headers={"Authorization": "Bearer " + tok},
                              json={"oldPassword": TEMP_PWD, "newPassword": FINAL_PWD},
                              timeout=10).json()
            check(f"改密 123321 {u}", r2.get("code") == 0, str(r2.get("msg", "")))
        elif login(u, FINAL_PWD):
            check(f"改密 123321 {u}", True, "(已是 123321,跳过)")
        else:
            check(f"改密 123321 {u}", False, "临时密与 123321 均登录失败")
            continue

        check(f"终验登录 {u}/123321", login(u, FINAL_PWD) is not None)

    # 工号反显抽查:教师 token 查 210003,应回姓名/院部/职称(登记表成员反显同款接口)
    tok_t = login("210001", FINAL_PWD)
    if tok_t:
        r3 = requests.get(BASE + "/hx/member-lookup", params={"q": "210003"},
                          headers={"Authorization": "Bearer " + tok_t}, timeout=10).json()
        d = r3.get("data") or {}
        ok = r3.get("code") == 0 and d.get("name") == "张建华" and d.get("deptName") == "信息工程学院"
        check("工号反显 member-lookup 210003", ok, str(d))

    bad = [n for n, ok in results if not ok]
    print(f"\n共 {len(results)} 项,失败 {len(bad)} 项" + (":" + ";".join(bad) if bad else ""))
    print("提醒:学位(degree)请另行 SQL 补齐,8 行 UPDATE 见会话命令")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
