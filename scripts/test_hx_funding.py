# -*- coding: utf-8 -*-
"""
到账+项目管理+合同附件接口冒烟(N4 Task25,spec §4.8/§6.3):
①admin 录入到账→教师通知(未读数)+教师只读明细/累计 ②删→累计回落+核销通知
③越权(教师录/删、teacher2 读、教师查项目列表)④合同附件上传→详情可见→重传覆盖
  (任何状态可传不迁移,含草稿态/已确认态)⑤GET /hx/projects 聚合(到账累计/执行状态推导:
  在研→变更中→已结题/在途流程单概要;含结题后补录=与状态解耦)。
前置:应用已启动(dev),四类模板启用版在库,LibreOffice 就位;admin/admin123、
teacher/teacher123、teacher2/teacher2123(dev 种子)。
"""
import re
import sys
from datetime import date
from decimal import Decimal

import requests

BASE = "http://localhost:8080/api"
results = []


def dec(v):
    """金额断言:JSON 数字→Python float 丢失标度,统一 Decimal 数值比较"""
    return None if v is None else Decimal(str(v))


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login(u, p):
    r = requests.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=10).json()
    return {"Authorization": "Bearer " + r["data"]["token"]}


def req(method, path, headers, timeout=120, **kw):
    """统一请求→dict;非 JSON 响应(404 空体等)回 code=-1 供统一断言"""
    try:
        return requests.request(method, BASE + path, headers=headers, timeout=timeout, **kw).json()
    except ValueError:
        return {"code": -1, "msg": "non-json response"}


def post(path, headers, body):
    return req("POST", path, headers, json=body)


def get(path, headers):
    return req("GET", path, headers)


def delete(path, headers):
    return req("DELETE", path, headers)


def detail(fid, headers):
    r = get("/hx/%d" % fid, headers)
    return r.get("data") or {}


def notify_unread(headers):
    return get("/notify/unread-count", headers).get("data")


def notify_has(headers, project_id, kw):
    """教师 /notify/my 首页含 projectId 匹配且标题含 kw 的消息"""
    page = get("/notify/my?size=50", headers).get("data") or {}
    return any(n.get("projectId") == project_id and kw in (n.get("title") or "")
               for n in page.get("records") or [])


FAKE_SCAN = b"%PDF-1.4 fake scan"
FAKE_CONTRACT = b"%PDF-1.4 fake contract"


def scan(headers, fid, doc_type):
    return req("POST", "/hx/%d/scan" % fid, headers, data={"docType": doc_type},
               files={"file": ("scan.pdf", FAKE_SCAN)})


def attach(headers, fid, doc_type="合同", name="contract.pdf", content=None):
    return req("POST", "/hx/%d/attach" % fid, headers, data={"docType": doc_type},
               files={"file": (name, content if content is not None else FAKE_CONTRACT)})


def reg_payload(name):
    """登记载荷(deptId=9=种子院部,Task23 seed_dept_dev.sql;预算=合同经费,规则R1)"""
    return {
        "name": name, "leaderName": "教师甲", "deptId": 9,
        "hx": {"projectType": "技术开发", "clientName": "到账测试委托单位", "clientNature": "企业",
               "contractAmount": "300000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
               "useSchoolAsset": 0, "chargeMgmtFee": 1,
               "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                     "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "300000.00"}],
    }


def register_confirmed(t, admin, name):
    """全链造一个已确认(在研)项目:登记→提交→回扫→审批通过;返回 (flowId, projectId)"""
    fid = post("/hx/register/draft", t, reg_payload(name))["data"]
    assert post("/hx/register/submit", t, {"flowId": fid}).get("code") == 0, name + " 登记提交失败"
    assert post("/hx/%d/precheck" % fid, admin, {}).get("code") == 0, name + " 登记预审失败"
    assert scan(t, fid, "登记表").get("code") == 0, name + " 登记回扫失败"
    assert post("/hx/%d/review" % fid, admin, {}).get("code") == 0, name + " 登记审批失败"
    return fid, detail(fid, t)["project"]["id"]


def eff_contracts(d, fid):
    """详情中该流程单生效的合同附件(hx_apply/合同/apply 目录段)"""
    return [a for a in (d.get("attachments") or [])
            if a.get("bizType") == "hx_apply" and a.get("docType") == "合同"
            and ("/apply/%d/" % fid) in (a.get("filePath") or "")]


def main():
    t = login("teacher", "teacher123")
    t2 = login("teacher2", "teacher2123")
    admin = login("admin", "admin123")
    today = date.today().isoformat()

    # ---- 造数:F=已确认在研(到账主战场) H=验收走完=结题(解耦补录) G=登记草稿(attach 状态下限) ----
    fid_f, pid_f = register_confirmed(t, admin, "到账测试-在研项目甲")
    fid_h, pid_h = register_confirmed(t, admin, "到账测试-结题补录项目乙")
    # HTML转正 R6:验收核算前置——先录到账 100000,成员配平(100%/100000)使用 100000
    assert post("/hx/funding", admin, {"projectId": pid_h, "amount": "100000.00",
                                       "receivedDate": "2027-10-01", "payer": "到账测试委托单位"}
                ).get("code") == 0, "乙 验收前置到账失败"
    acc = {"projectId": pid_h, "usedAmount": "100000.00", "isOnTime": 1,
           "outcomeForm": "技术报告", "acceptSummary": "全部委托内容已完成并验收",
           "reportContent": "一、项目目标达成情况\n\n按合同完成全部内容。",
           "members": [{"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                        "title": "教授", "major": "机械工程", "duty": "负责", "ratio": "100.00",
                        "amount": "100000.00", "sortNo": 1}]}
    afid = post("/hx/acceptance/draft", t, acc)["data"]
    assert post("/hx/acceptance/submit", t, {"flowId": afid}).get("code") == 0, "乙 验收提交失败"
    assert post("/hx/%d/precheck" % afid, admin, {}).get("code") == 0, "乙 验收预审失败"
    assert scan(t, afid, "验收登记表").get("code") == 0, "乙 验收回扫失败"
    assert post("/hx/%d/review" % afid, admin, {"verifiedAmount": 100000.00}).get("code") == 0, "乙 验收审批失败"
    assert detail(afid, t)["project"]["status"] == "结题", "乙 未结题"
    fid_g = post("/hx/register/draft", t, reg_payload("到账测试-草稿附件项目丙"))["data"]

    # ---------------- ① 录入:校验 + 两笔 + 通知 ----------------
    check("金额必须大于0", post("/hx/funding", admin, {"projectId": pid_f, "amount": "0.00",
          "receivedDate": today}).get("code") != 0, "")
    check("到账日期必填", post("/hx/funding", admin, {"projectId": pid_f, "amount": "1.00"}).get("code") != 0, "")
    check("项目不存在拒", post("/hx/funding", admin, {"projectId": 99999999, "amount": "1.00",
          "receivedDate": today}).get("code") != 0, "")
    u0 = notify_unread(t)
    r1 = post("/hx/funding", admin, {"projectId": pid_f, "amount": "60000.00", "receivedDate": today,
                                     "payer": "到账测试委托单位", "bankFlowNo": "FLOW-2026-001", "remark": "首笔"})
    r2 = post("/hx/funding", admin, {"projectId": pid_f, "amount": "25000.50", "receivedDate": today})
    id1, id2 = r1.get("data"), r2.get("data")
    check("admin 录入两笔到账", r1.get("code") == 0 and r2.get("code") == 0 and id1 and id2,
          "%s / %s" % (r1.get("msg"), r2.get("msg")))
    check("录入即教师通知(未读数+标题)", u0 is not None and notify_unread(t) >= u0 + 2
          and notify_has(t, pid_f, "到账"), "unread %s→%s" % (u0, notify_unread(t)))
    d = (get("/hx/%d/funding" % pid_f, t).get("data") or {})
    recs = d.get("records") or []
    check("教师只读明细+累计(台账升序)", len(recs) == 2 and dec(d.get("total")) == Decimal("85000.50")
          and dec(recs[0]["amount"]) == Decimal("60000.00")
          and recs[0].get("payer") == "到账测试委托单位"
          and recs[0].get("bankFlowNo") == "FLOW-2026-001",
          "n=%d total=%s" % (len(recs), d.get("total")))

    # ---------------- ② 删除:累计回落+核销通知 ----------------
    r = delete("/hx/funding/%s" % id1, admin)
    d = (get("/hx/%d/funding" % pid_f, t).get("data") or {})
    recs = d.get("records") or []
    check("删到账累计回落+核销通知", r.get("code") == 0 and len(recs) == 1
          and dec(d.get("total")) == Decimal("25000.50") and notify_has(t, pid_f, "核销"),
          "n=%d total=%s" % (len(recs), d.get("total")))
    check("重复删被拒", delete("/hx/funding/%s" % id1, admin).get("code") != 0, "")

    # ---------------- ③ 越权三向 ----------------
    check("教师不可录/删到账", post("/hx/funding", t, {"projectId": pid_f, "amount": "1.00",
          "receivedDate": today}).get("code") != 0
          and delete("/hx/funding/%s" % id2, t).get("code") != 0, "")
    check("teacher2 越权读被拒", get("/hx/%d/funding" % pid_f, t2).get("code") != 0, "")
    check("教师不可查项目列表/admin按项目查", get("/hx/projects", t).get("code") != 0
          and dec((get("/hx/funding?projectId=%d" % pid_f, admin).get("data") or {}).get("total"))
          == Decimal("25000.50"), "")

    # ---------------- ④ 结题后补录(与项目状态解耦,spec §4.8) ----------------
    r = post("/hx/funding", admin, {"projectId": pid_h, "amount": "10000.00", "receivedDate": today})
    d = (get("/hx/%d/funding" % pid_h, t).get("data") or {})
    check("结题后仍可补录", r.get("code") == 0
          and dec(d.get("total")) == Decimal("110000.00"),   # 验收前置 100000+补录 10000
          str(r.get("msg")))

    # ---------------- ⑤ 合同附件(任何状态可传不迁移) ----------------
    st_f = detail(fid_f, t).get("flow", {}).get("status")   # 已通过(3)状态的单
    a1 = attach(t, fid_f)
    d = detail(fid_f, t)
    atts = eff_contracts(d, fid_f)
    check("合同附件上传详情可见", a1.get("code") == 0 and a1.get("data") and len(atts) == 1
          and d.get("flow", {}).get("status") == st_f,
          "atts=%d status=%s" % (len(atts), d.get("flow", {}).get("status")))
    a2 = attach(t, fid_f, name="contract_v2.pdf")
    d = detail(fid_f, t)
    atts = eff_contracts(d, fid_f)
    check("重传同类型覆盖", a2.get("code") == 0 and len(atts) == 1
          and atts[0]["id"] != a1.get("data") and atts[0]["fileName"] == "contract_v2.pdf",
          "n=%d" % len(atts))
    check("草稿态可传且不迁移", attach(t, fid_g).get("code") == 0
          and detail(fid_g, t).get("flow", {}).get("status") == 0, "")
    check("附件类型不合法拒", attach(t, fid_f, doc_type="登记表").get("code") != 0, "")
    check("附件格式不合法拒", attach(t, fid_f, name="contract.docx").get("code") != 0, "")
    check("空文件拒", attach(t, fid_f, content=b"").get("code") != 0, "")
    check("他人流程上传拒", attach(t2, fid_f).get("code") != 0, "")

    # ---------------- ⑥ GET /hx/projects 聚合(决策#21) ----------------
    rows = get("/hx/projects", admin).get("data") or []
    frow = next((x for x in rows if x.get("projectId") == pid_f), None)
    hrow = next((x for x in rows if x.get("projectId") == pid_h), None)
    check("项目列表聚合字段(在研)", frow is not None and frow.get("execStatus") == "在研"
          and dec(frow.get("receivedTotal")) == Decimal("25000.50")
          and re.match(r"^HX%d-\d{3,}$" % date.today().year, frow.get("approvalNo") or "")
          and frow.get("clientName") and frow.get("deptName")
          and dec(frow.get("contractAmount")) == Decimal("300000.00")
          and frow.get("hxRegisterDate") == today and frow.get("pendingFlows") == [],
          str(frow)[:160] if frow else "无 F 行")
    check("项目列表(已结题+到账累计)", hrow is not None and hrow.get("execStatus") == "已结题"
          and dec(hrow.get("receivedTotal")) == Decimal("110000.00") and hrow.get("finishDate"),
          str(hrow)[:140] if hrow else "无 H 行")
    # 发起在途变更单→执行状态推导为「变更中」+在途概要;顺带校验 id 倒序(H 新于 F)
    chg = {"projectId": pid_f, "changeType": "项目经费", "changeContent": "经费调增",
           "changeReason": "委托方追加需求", "newAmount": "330000.00"}
    cfid = post("/hx/change/draft", t, chg)["data"]
    assert post("/hx/change/submit", t, {"flowId": cfid}).get("code") == 0, "F 变更提交失败"
    rows = get("/hx/projects", admin).get("data") or []
    frow = next((x for x in rows if x.get("projectId") == pid_f), None)
    pend = (frow or {}).get("pendingFlows") or []
    check("在途变更→变更中+在途概要", frow is not None and frow.get("execStatus") == "变更中"
          and any(p.get("flowType") == "change" for p in pend)
          and rows.index(hrow) < rows.index(frow), str(pend)[:120])

    # 收尾:G 草稿连带附件逻辑删(既有能力回归)
    check("草稿G清理", delete("/hx/%d" % fid_g, t).get("code") == 0, "")

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
