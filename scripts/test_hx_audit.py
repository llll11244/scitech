# -*- coding: utf-8 -*-
"""
科技处审批接口冒烟(N4 Task24):审批列表/三流程通过副作用(登记编号入账/变更改写/验收结题+终版两张)/
退回(预检+常规)/教师重交(旧待签+旧扫描件作废)/越权与幂等。
本脚本首次贯通 N3 卡住的全链路:登记确认(audit_status=2)→变更/验收提交→通过生效(spec §6.1)。
前置:应用已启动(dev),四类模板启用版在库,LibreOffice 就位;admin/admin123、teacher/teacher123、
teacher2/teacher2123(dev 种子);viewer 无 dev 账号,非 admin 拒绝以 teacher 角色覆盖(同一 requireAdmin 路径)。
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


def post(path, headers, body, timeout=120):
    """POST→dict;非 JSON 响应(404 空体等)回 code=-1 供统一断言"""
    try:
        return requests.post(BASE + path, headers=headers, json=body, timeout=timeout).json()
    except ValueError:
        return {"code": -1, "msg": "non-json response"}


def get(path, headers, timeout=30):
    try:
        return requests.get(BASE + path, headers=headers, timeout=timeout).json()
    except ValueError:
        return {"code": -1, "msg": "non-json response"}


def detail(fid, headers):
    return get("/hx/%d" % fid, headers)["data"]


def notify_has(headers, project_id, kw):
    """教师 /notify/my 首页含 projectId 匹配且标题含 kw 的消息"""
    page = get("/notify/my?size=50", headers)["data"]
    return any(n.get("projectId") == project_id and kw in (n.get("title") or "") for n in page["records"])


FAKE_SCAN = b"%PDF-1.4 fake scan"


def scan(headers, fid, doc_type):
    return requests.post(BASE + "/hx/%d/scan" % fid, headers=headers, data={"docType": doc_type},
                         files={"file": ("scan.pdf", FAKE_SCAN)}, timeout=30).json()


def eff_atts(d, fid, biz=None):
    """详情生效附件(作废不入列表=§3.2),按流程单目录段过滤,可选 biz_type 过滤"""
    rows = [a for a in d["attachments"] if ("/%d/" % fid) in (a.get("filePath") or "")]
    if biz is not None:
        rows = [a for a in rows if a["bizType"] == biz]
    return rows


def main():
    t = login("teacher", "teacher123")
    t2 = login("teacher2", "teacher2123")
    admin = login("admin", "admin123")
    year = date.today().year
    no_re = re.compile(r"^HX%d-\d{3,}$" % year)

    # ---------------- 项目A(teacher):登记→通过→变更(三改写)→通过→验收→通过(终版两张) ----------------
    # deptId=9=种子院部「智能制造与装备学院」(Task23 scripts/seed_dept_dev.sql,id 从 9 起)
    reg_a = {
        "name": "审批测试-智能纺织装备升级改造", "leaderName": "教师甲", "deptId": 9,
        "hx": {"projectType": "技术开发", "clientName": "审批测试委托单位", "clientNature": "企业",
               "contractAmount": "355000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
               "useSchoolAsset": 0, "chargeMgmtFee": 1, "mgmtFeeAmount": "10000.00",
               "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                      "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "80000.00"}],
    }
    fid_a = post("/hx/register/draft", t, reg_a)["data"]
    assert post("/hx/register/submit", t, {"flowId": fid_a}).get("code") == 0, "A 登记提交失败"
    assert scan(t, fid_a, "登记表").get("code") == 0, "A 登记回扫失败"
    pid_a = detail(fid_a, t)["project"]["id"]

    # 1. 审批列表(admin;教师拒——viewer 同 requireAdmin 路径无 dev 账号,以 teacher 覆盖)
    r = get("/hx/audit?flowType=register&status=2", admin)
    row = next((x for x in (r.get("data") or []) if x.get("flowId") == fid_a), None)
    check("审批列表聚合项目信息", r.get("code") == 0 and row is not None
          and row.get("name") == reg_a["name"] and row.get("leaderName") == "教师甲"
          and row.get("deptName") == "智能制造与装备学院" and row.get("submitTime"),
          str(row)[:120] if row else "未找到 flowId=%d" % fid_a)
    check("审批列表教师被拒", get("/hx/audit", t).get("code") != 0, "")

    # 2. 登记通过:入台账+编号+立项日期+通知
    r = post("/hx/%d/review" % fid_a, admin, {})
    d = detail(fid_a, admin)
    p = d["project"]
    check("登记通过入台账", r.get("code") == 0 and d["flow"]["status"] == 3
          and p["auditStatus"] == 2 and p["status"] == "立项"
          and no_re.match(p.get("approvalNo") or ""),
          "status=%s audit=%s no=%s" % (d["flow"]["status"], p["auditStatus"], p.get("approvalNo")))
    check("登记确认日/确认时间", d["hx"].get("hxRegisterDate") == str(date.today())
          and d["flow"].get("confirmTime") is not None,
          "hxRegisterDate=%s confirmTime=%s" % (d["hx"].get("hxRegisterDate"), d["flow"].get("confirmTime")))
    check("登记通过通知教师", notify_has(t, pid_a, "登记"),
          "projectId=%s" % pid_a)
    check("登记时间线 approve", "approve" in [x["action"] for x in d["records"]], "")

    # 3. 变更全链(audit_status=2 已确认→提交放行,N3 卡点首次贯通):三类型改写
    leaders = get("/hx/leaders?q=teacher2", t)["data"]
    t2_id = leaders[0]["id"]
    chg = {"projectId": pid_a, "changeType": "项目经费,执行周期,项目负责人",
           "changeContent": "经费调增+延期半年+负责人变更为教师乙",
           "changeReason": "委托方追加需求并协商换负责人",
           "newAmount": "420000.00", "newEndDate": "2027-06-30", "newLeaderId": t2_id}
    cfid = post("/hx/change/draft", t, chg)["data"]
    r = post("/hx/change/submit", t, {"flowId": cfid})
    check("变更提交(已确认项目放行)", r.get("code") == 0, str(r.get("msg")))
    d = detail(cfid, t)
    # 金额数字断言不带标度:MySQL JSON 列规范化会丢尾零(355000.00→355000.0)
    check("变更 before_json 快照", "355000" in (d["flow"].get("beforeJson") or "")
          and "2027-12-31" in (d["flow"].get("beforeJson") or ""),
          str(d["flow"].get("beforeJson"))[:120])
    assert scan(t, cfid, "变更表").get("code") == 0, "A 变更回扫失败"
    r = post("/hx/%d/review" % cfid, admin, {})
    d = detail(cfid, admin)
    check("变更通过三改写", r.get("code") == 0 and d["flow"]["status"] == 3
          and dec(d["hx"]["contractAmount"]) == Decimal("420000.00")
          and str(d["hx"]["endDate"]) == "2027-06-30"
          and d["project"]["applyUserId"] == t2_id and d["project"]["leaderName"] == "测试教师乙",
          "amount=%s end=%s owner=%s leader=%s" % (d["hx"]["contractAmount"], d["hx"]["endDate"],
                                                    d["project"]["applyUserId"], d["project"]["leaderName"]))
    check("变更不改台账状态/通知新负责人", d["project"]["auditStatus"] == 2 and d["project"]["status"] == "立项"
          and notify_has(t2, pid_a, "变更"),
          "audit=%s status=%s" % (d["project"]["auditStatus"], d["project"]["status"]))

    # 4. 验收(项目已易主 teacher2):verifiedAmount 必填→结题+终版两张
    acc = {"projectId": pid_a, "usedAmount": "410000.00", "isOnTime": 1,
           "outcomeForm": "技术报告,专利", "acceptSummary": "全部委托内容已完成并验收",
           "reportContent": "一、项目目标达成情况\n\n按合同完成全部改造内容并上线运行。\n\n二、经费使用情况\n\n使用经费41万元。",
           "members": [{"memberType": "教师", "name": "教师乙", "unit": "智能制造与装备学院",
                        "title": "讲师", "major": "自动化", "duty": "系统联调",
                        "ratio": "100.00", "amount": "410000.00", "sortNo": 1}]}
    afid = post("/hx/acceptance/draft", t2, acc)["data"]
    assert post("/hx/acceptance/submit", t2, {"flowId": afid}).get("code") == 0, "A 验收提交失败"
    assert scan(t2, afid, "验收登记表").get("code") == 0, "A 验收回扫失败"
    r = post("/hx/%d/review" % afid, admin, {})   # 缺 verifiedAmount → 必拦
    check("验收核定金额必填", r.get("code") != 0 and "必填" in (r.get("msg") or ""), str(r.get("msg")))
    r = post("/hx/%d/review" % afid, admin, {"verifiedAmount": 415000.00})
    d = detail(afid, admin)
    p = d["project"]
    finals = sorted(a["bizType"] for a in eff_atts(d, afid)
                    if a["bizType"].startswith("hx_final"))
    check("验收通过结题+核定额", r.get("code") == 0 and d["flow"]["status"] == 3
          and p["status"] == "结题" and str(p.get("finishDate")) == str(date.today())
          and dec(d["hx"]["verifiedAmount"]) == Decimal("415000.00"),
          "status=%s finish=%s verified=%s" % (p["status"], p.get("finishDate"), d["hx"]["verifiedAmount"]))
    check("终版两张四附件", finals == ["hx_final_docx", "hx_final_pdf",
                                       "hx_final_report_docx", "hx_final_report_pdf"], str(finals))
    check("验收时间线 verify+通知", "verify" in [x["action"] for x in d["records"]]
          and notify_has(t2, pid_a, "验收"), "")

    # 5. 重复通过幂等拒(状态已 3)
    r = post("/hx/%d/review" % afid, admin, {"verifiedAmount": 1.00})
    check("重复通过被拒", r.get("code") != 0, str(r.get("msg")))

    # ---------------- 项目B(teacher):预检退回→改数据重交→常规退回→直接重交→通过(编号唯一) ----------------
    reg_b = dict(reg_a, name="审批测试-退回重交演练项目")
    fid_b = post("/hx/register/draft", t, reg_b)["data"]
    assert post("/hx/register/submit", t, {"flowId": fid_b}).get("code") == 0, "B 登记提交失败"

    # 6. 盖章前预检退回(status=1,isPrecheck=true→action=precheck_reject)
    r = post("/hx/%d/reject" % fid_b, admin, {"opinion": "经费科目填错,盖章前改", "isPrecheck": True})
    d = detail(fid_b, t)
    acts = [x["action"] for x in d["records"]]
    check("预检退回(status1)", r.get("code") == 0 and d["flow"]["status"] == 4
          and "precheck_reject" in acts, str(acts))
    check("退回意见必填", post("/hx/%d/reject" % fid_b, admin, {"opinion": " "}).get("code") != 0, "")

    # 7. 退回态(status=4)教师可改草稿数据→重交
    r = post("/hx/register/draft", t, dict(reg_b, flowId=fid_b, name="审批测试-退回重交演练项目(修订)"))
    d = detail(fid_b, t)
    check("退回态可编辑草稿", r.get("code") == 0 and d["project"]["name"].endswith("(修订)")
          and d["flow"]["status"] == 4, "name=%s status=%s" % (d["project"]["name"], d["flow"]["status"]))
    r = post("/hx/%d/resubmit" % fid_b, t, {})
    d = detail(fid_b, t)
    check("重交回已提交", r.get("code") == 0 and d["flow"]["status"] == 1
          and "resubmit" in [x["action"] for x in d["records"]]
          and len(eff_atts(d, fid_b, "hx_docx")) == 1, "status=%s" % d["flow"]["status"])

    # 8. 回扫→常规退回(status=2)→重交:旧扫描件一并作废(spec §3.2)
    assert scan(t, fid_b, "登记表").get("code") == 0, "B 回扫失败"
    d = detail(fid_b, t)
    assert len(eff_atts(d, fid_b, "hx_scan")) == 1, "前置:生效扫描件应为 1"
    r = post("/hx/%d/reject" % fid_b, admin, {"opinion": "盖章页缺页,补章后重传"})
    d = detail(fid_b, t)
    check("常规退回(status2)", r.get("code") == 0 and d["flow"]["status"] == 4
          and "reject" in [x["action"] for x in d["records"]], "status=%s" % d["flow"]["status"])
    r = post("/hx/%d/resubmit" % fid_b, t, {})
    d = detail(fid_b, t)
    check("重交作废旧扫描件+旧待签重渲", r.get("code") == 0 and d["flow"]["status"] == 1
          and len(eff_atts(d, fid_b, "hx_scan")) == 0
          and len(eff_atts(d, fid_b, "hx_docx")) == 1 and len(eff_atts(d, fid_b, "hx_pdf")) == 1,
          "scan=%d docx=%d pdf=%d" % (len(eff_atts(d, fid_b, "hx_scan")),
                                      len(eff_atts(d, fid_b, "hx_docx")), len(eff_atts(d, fid_b, "hx_pdf"))))

    # 9. 重新回扫→通过:编号与 A 唯一且格式合法
    assert scan(t, fid_b, "登记表").get("code") == 0, "B 二次回扫失败"
    r = post("/hx/%d/review" % fid_b, admin, {})
    d = detail(fid_b, admin)
    no_a = detail(fid_a, admin)["project"]["approvalNo"]
    no_b = d["project"]["approvalNo"]
    check("通过且编号唯一合法", r.get("code") == 0 and d["flow"]["status"] == 3
          and no_re.match(no_b or "") and no_a != no_b, "A=%s B=%s" % (no_a, no_b))

    # 10. 越权:教师不可调 admin 审批接口
    check("教师不可通过/退回/列表", post("/hx/%d/review" % fid_b, t, {}).get("code") != 0
          and post("/hx/%d/reject" % fid_b, t, {"opinion": "x"}).get("code") != 0
          and get("/hx/audit", t).get("code") != 0, "")

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
