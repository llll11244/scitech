# -*- coding: utf-8 -*-
"""
N4 Task26 后端小改冒烟(终版下载/字典管理保护/最小用户管理/newLeaderName):
①终版下载:走完验收结题的单 deliver-final docx/pdf 可取(教师本人+admin),
  未通过单拒、非本人拒 ②字典:PUT 停启用/调序 ok、is_system=1 label 改/删拒,
  自建项可删,模板联动类型增/删响应带 remind,非联动不带,/dicts/manage 全量(含停用)
  ③用户管理:建号(强度/判重)、停用即 401+登录拒、不可自停、重置密码、
  最近登录落库 ④变更单 flowDetail.newLeaderName 回显。
前置:应用已启动(dev),四类模板启用版在库,LibreOffice 就位;
admin/admin123、teacher/teacher123、teacher2/teacher2123(dev 种子)。
"""
import sys

import requests

BASE = "http://localhost:8080/api"
results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login(u, p):
    r = requests.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=10).json()
    return {"Authorization": "Bearer " + r["data"]["token"]}


def req(method, path, headers, timeout=120, **kw):
    """统一请求→dict;非 JSON 响应(下载流)回 code=-2 供统一断言"""
    try:
        return requests.request(method, BASE + path, headers=headers, timeout=timeout, **kw).json()
    except ValueError:
        return {"code": -2, "msg": "non-json response"}


def post(path, headers, body):
    return req("POST", path, headers, json=body)


def get(path, headers):
    return req("GET", path, headers)


def put(path, headers, body):
    return req("PUT", path, headers, json=body)


def delete(path, headers):
    return req("DELETE", path, headers)


def raw(method, path, headers, **kw):
    """二进制下载:返回 (status, content)——业务错误走 HTTP200+Result JSON,按 code 判"""
    r = requests.request(method, BASE + path, headers=headers, timeout=120, **kw)
    if r.headers.get("Content-Type", "").startswith("application/json"):
        return r.json().get("code"), r.content
    return r.status_code, r.content


def scan(headers, fid, doc_type):
    return req("POST", "/hx/%d/scan" % fid, headers, data={"docType": doc_type},
               files={"file": ("scan.pdf", b"%PDF-1.4 fake scan")})


def reg_payload(name):
    return {
        "name": name, "leaderName": "教师甲", "deptId": 9,
        "hx": {"projectType": "技术开发", "clientName": "Task26测试委托单位", "clientNature": "企业",
               "contractAmount": "200000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
               "useSchoolAsset": 0, "chargeMgmtFee": 0,
               "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                     "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "50000.00"}],
    }


def register_confirmed(t, admin, name):
    """登记→提交→回扫→审批通过,返回 (flowId, projectId)"""
    fid = post("/hx/register/draft", t, reg_payload(name))["data"]
    assert post("/hx/register/submit", t, {"flowId": fid}).get("code") == 0, name + " 登记提交失败"
    assert scan(t, fid, "登记表").get("code") == 0, name + " 登记回扫失败"
    assert post("/hx/%d/review" % fid, admin, {}).get("code") == 0, name + " 登记审批失败"
    return fid, get("/hx/%d" % fid, t)["data"]["project"]["id"]


def main():
    t = login("teacher", "teacher123")
    t2 = login("teacher2", "teacher2123")
    admin = login("admin", "admin123")

    # ================ ① 终版下载(结题项目) ================
    fid_reg, pid = register_confirmed(t, admin, "Task26终版测试项目")
    acc = {"projectId": pid, "usedAmount": "80000.00", "isOnTime": 1,
           "outcomeForm": "技术报告", "acceptSummary": "全部委托内容已完成并验收",
           "reportContent": "一、项目目标达成情况\n\n按合同完成全部内容。",
           "members": [{"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                        "title": "教授", "major": "机械工程", "duty": "负责", "ratio": "100.00",
                        "amount": "80000.00", "sortNo": 1}]}
    fid_acc = post("/hx/acceptance/draft", t, acc)["data"]
    assert post("/hx/acceptance/submit", t, {"flowId": fid_acc}).get("code") == 0, "验收提交失败"
    assert scan(t, fid_acc, "验收登记表").get("code") == 0, "验收回扫失败"
    assert post("/hx/%d/review" % fid_acc, admin, {"verifiedAmount": 80000.00}).get("code") == 0, "验收审批失败"
    assert post("/hx/%d/review" % fid_acc, admin, {}).get("code") != 0, "重复通过未拒"

    code, body = raw("GET", "/hx/%d/deliver-final/docx" % fid_acc, t)
    check("终版验收表 docx(教师本人)", code in (0, 200) and body[:2] == b"PK", "code=%s len=%d" % (code, len(body)))
    code, body = raw("GET", "/hx/%d/deliver-final/pdf?doc=hx_report" % fid_acc, t)
    check("终版报告 pdf(doc=hx_report)", code in (0, 200) and body[:4] == b"%PDF", "code=%s len=%d" % (code, len(body)))
    code, body = raw("GET", "/hx/%d/deliver-final/docx" % fid_acc, admin)
    check("终版下载 admin 可取", code in (0, 200) and body[:2] == b"PK", "code=%s" % code)
    code, body = raw("GET", "/hx/%d/deliver-final/docx" % fid_acc, t2)
    check("终版下载非本人拒", code != 0, "code=%s" % code)
    code, body = raw("GET", "/hx/%d/deliver-final/docx" % fid_reg, t)
    check("登记单无终版拒(未验收确认)", code != 0, "code=%s" % code)
    # 未通过的单(造一张在途变更)也无终版
    chg = {"projectId": pid, "changeType": "项目经费", "changeContent": "经费调增",
           "changeReason": "委托方追加", "newAmount": "210000.00"}
    fid_chg = post("/hx/change/draft", t, chg)["data"]
    assert post("/hx/change/submit", t, {"flowId": fid_chg}).get("code") == 0, "变更提交失败"
    code, body = raw("GET", "/hx/%d/deliver-final/docx" % fid_chg, t)
    check("在途变更单无终版拒", code != 0, "code=%s" % code)

    # ================ ④ 变更单 newLeaderName 回显(独立项目,避开上面的在途变更单) ================
    _, pid2 = register_confirmed(t, admin, "Task26变更回显项目")
    leader2 = next((x for x in (get("/hx/leaders?q=teacher2", t).get("data") or [])
                    if x.get("username") == "teacher2"), None)
    chg2 = {"projectId": pid2, "changeType": "项目负责人,执行周期", "changeContent": "换负责人并延期",
            "changeReason": "原负责人调离", "newEndDate": "2028-06-30",
            "newLeaderId": leader2 and leader2.get("id")}
    r2 = post("/hx/change/draft", t, chg2)
    assert r2.get("code") == 0, "负责人变更草稿失败:%s" % r2.get("msg")
    d = get("/hx/%d" % r2["data"], t).get("data") or {}
    check("变更单 newLeaderName 回显", d.get("newLeaderName") == (leader2 or {}).get("name"),
          "newLeaderName=%s" % d.get("newLeaderName"))

    # ================ ② 字典管理 ================
    check("manage 教师拒", get("/dicts/manage?type=member_type", t).get("code") != 0, "")
    rows = (get("/dicts/manage?type=member_type", admin).get("data") or [])
    sys_row = next((r for r in rows if r.get("dictValue") == "教师"), None)
    check("manage 全量含内置项", sys_row is not None and sys_row.get("isSystem") == 1,
          str(len(rows)))
    # 内置保护:label 改拒/删拒,停启用+调序 ok
    check("内置 label 改拒", put("/dicts/%d" % sys_row["id"], admin,
          {"label": "老师"}).get("code") != 0, "")
    check("内置删拒", delete("/dicts/%d" % sys_row["id"], admin).get("code") != 0, "")
    check("内置停用 ok", put("/dicts/%d" % sys_row["id"], admin, {"status": 0}).get("code") == 0, "")
    visible = (get("/dicts?type=member_type", t).get("data") or [])
    manage = (get("/dicts/manage?type=member_type", admin).get("data") or [])
    check("停用后下拉隐藏/manage 仍可见",
          all(r.get("dictValue") != "教师" for r in visible)
          and any(r.get("dictValue") == "教师" and r.get("status") == 0 for r in manage), "")
    check("内置启用还原", put("/dicts/%d" % sys_row["id"], admin,
          {"status": 1, "sortOrder": 1}).get("code") == 0, "")
    # 联动类型增/删带 remind;非联动不带(先清理历史残留,保证可重跑)
    def dict_add(dtype, value, label):
        return req("POST", "/dicts", admin, params={"type": dtype, "value": value, "label": label})

    for dtype, value in (("hx_outcome_form", "测试新成果"), ("member_type", "博士后")):
        stale = next((x for x in (get("/dicts/manage?type=%s" % dtype, admin).get("data") or [])
                      if x.get("dictValue") == value), None)
        if stale and stale.get("isSystem") == 0:
            delete("/dicts/%d" % stale["id"], admin)
    r = dict_add("hx_outcome_form", "测试新成果", "测试新成果")
    check("联动类型新增带 remind", r.get("code") == 0 and "模板" in ((r.get("data") or {}).get("remind") or ""),
          str(r.get("data")))
    new_row = next((x for x in (get("/dicts/manage?type=hx_outcome_form", admin).get("data") or [])
                    if x.get("dictValue") == "测试新成果"), None)
    check("新增项 manage 可见", new_row is not None, "")
    r = dict_add("member_type", "博士后", "博士后")
    check("非联动新增不带 remind", r.get("code") == 0 and not (r.get("data") or {}).get("remind"), "")
    postdoc = next((x for x in (get("/dicts/manage?type=member_type", admin).get("data") or [])
                    if x.get("dictValue") == "博士后"), None)
    check("自建项改 label ok", put("/dicts/%d" % postdoc["id"], admin,
          {"label": "博士后人员"}).get("code") == 0, "")
    r = delete("/dicts/%d" % new_row["id"], admin)
    check("自建项可删+联动删带 remind", r.get("code") == 0
          and "模板" in ((r.get("data") or {}).get("remind") or ""), str(r.get("data")))
    check("删后 manage 不可见", all(x.get("dictValue") != "测试新成果" for x in
          (get("/dicts/manage?type=hx_outcome_form", admin).get("data") or [])), "")
    check("删后同取值可重加(物理删不占唯一键)", dict_add("hx_outcome_form", "测试新成果",
          "测试新成果").get("code") == 0, "")
    redo = next((x for x in (get("/dicts/manage?type=hx_outcome_form", admin).get("data") or [])
                 if x.get("dictValue") == "测试新成果"), None)
    if redo:
        delete("/dicts/%d" % redo["id"], admin)   # 清理
    check("重复取值拒", dict_add("member_type", "教师", "教师2").get("code") != 0, "")
    pd = next((x for x in (get("/dicts/manage?type=member_type", admin).get("data") or [])
               if x.get("dictValue") == "博士后"), None)
    if pd:
        delete("/dicts/%d" % pd["id"], admin)   # 清理自建项

    # ================ ③ 用户管理 ================
    check("用户列表教师拒", get("/users", t).get("code") != 0, "")
    page = (get("/users?page=1&size=50", admin).get("data") or {})
    admin_row = next((r for r in page.get("records") or [] if r.get("username") == "admin"), None)
    check("用户列表分页+最近登录落库", admin_row is not None and (page.get("total") or 0) >= 3
          and admin_row.get("lastLogin"), str(admin_row)[:150])
    check("用户搜索过滤", len(get("/users?q=teacher", admin).get("data", {}).get("records") or []) >= 2
          and all("teacher" in (r.get("username") or "") for r in
                  get("/users?q=teacher", admin).get("data", {}).get("records") or []), "")
    check("建号弱密码拒", post("/users", admin, {"username": "t26user", "realName": "测试员",
          "role": "teacher", "password": "1234abc"}).get("code") != 0, "")
    check("建号纯数字拒", post("/users", admin, {"username": "t26user", "realName": "测试员",
          "role": "teacher", "password": "12345678"}).get("code") != 0, "")
    check("建号角色非法拒", post("/users", admin, {"username": "t26user", "realName": "测试员",
          "role": "boss", "password": "Abcd1234"}).get("code") != 0, "")
    r = post("/users", admin, {"username": "t26user", "realName": "测试员二十六",
                               "role": "teacher", "password": "Abcd1234", "deptId": 9})
    if r.get("code") != 0:
        # 可重跑:上次运行留下的 t26user(口令已被改/停用)→ 重置口令+启用后继续
        stale = next((x for x in (get("/users?q=t26user", admin).get("data") or {}).get("records") or []
                      if x.get("username") == "t26user"), None)
        assert stale, "建号失败且无历史账号:%s" % r.get("msg")
        uid_new = stale["id"]
        put("/users/%s/password" % uid_new, admin, {"password": "Abcd1234"})
        put("/users/%s/status" % uid_new, admin, {"status": 1})
        check("建号 ok(复用历史测试号)", True, "")
    else:
        check("建号 ok", r.get("code") == 0 and r.get("data"), str(r.get("msg")))
        uid_new = r.get("data")
    check("建号重复用户名拒", post("/users", admin, {"username": "t26user", "realName": "x",
          "role": "teacher", "password": "Abcd1234"}).get("code") != 0, "")
    # 新号可登录且 lastLogin 即刻有值
    t3 = login("t26user", "Abcd1234")
    row3 = next((x for x in (get("/users?q=t26user", admin).get("data") or {}).get("records") or []
                 if x.get("username") == "t26user"), None)
    check("新号可登录+最近登录", get("/users/me", t3).get("code") == 0
          and row3 is not None and row3.get("lastLogin"), str(row3)[:140])
    # 停用:登录拒 + 既有 token 立即 401(AuthInterceptor 停用即失效)
    check("停用 ok", put("/users/%s/status" % uid_new, admin, {"status": 0}).get("code") == 0, "")
    check("停用后登录拒", post("/auth/login", {"username": "t26user", "password": "Abcd1234"},
          {"Authorization": ""}).get("code") != 0, "")
    check("停用后既有 token 失效", get("/users/me", t3).get("code") != 0, "")
    check("不可自停", put("/users/1/status", admin, {"status": 0}).get("code") != 0, "")
    check("重新启用 ok", put("/users/%s/status" % uid_new, admin, {"status": 1}).get("code") == 0, "")
    check("重置密码弱口令拒", put("/users/%s/password" % uid_new, admin,
          {"password": "11111111"}).get("code") != 0, "")
    check("重置密码 ok", put("/users/%s/password" % uid_new, admin,
          {"password": "Xyze5678"}).get("code") == 0, "")
    t4 = login("t26user", "Xyze5678")
    check("新密码可登录", get("/users/me", t4).get("code") == 0, "")
    # 收尾:停用测试号(无删,留存停用态)
    put("/users/%s/status" % uid_new, admin, {"status": 0})

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
