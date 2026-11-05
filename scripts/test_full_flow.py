# -*- coding: utf-8 -*-
"""
单项目全流程回归(2026-11-05 审核):一个项目 P1 从登记走到结题的完整生命周期,
外加一次性探针项目 P2 承载破坏性用例(flowType 混用/金额量级/日期先后)。
既有边界(R1-R6/在途唯一/退回重交/取消/易主)与本次审核新增边界同场验证:
  A  flowType 校验:/hx/register/draft|submit 不得接受变更/验收单 id(修复前可绕锁定/污染 auditStatus)
  B  验收审批前扫描件齐全:验收登记表+验收报告两件缺一不可
  C  退回意见长度 ≤500(varchar(500))
  D  金额量级 ≤999999999999.99(DECIMAL(14,2))
  E  开始时间不得晚于结束时间(登记+变更执行周期)
  F  结题项目禁止教师再发起变更(admin 链路不受限)
  G  到账金额统一 setScale(2) 入库与通知同值
  H  变更/验收互斥(2026-11-05 拍板):在途变更禁提验收(③)+结题后批在途变更拒(②封竞态窗口)
  I  审批终态复检(二轮审核①):登记 R1/验收 R3-R6 在 2→3 批前按库中终态复检(教师自改/admin 删到账漂移)
  J  成员比例 0~100 保存级边界(二轮审核②):拦 DECIMAL(5,2) 溢出「系统异常」/负数/超100
前置:应用已启动(dev);admin/admin123,teacher/teacher2 密码 123(旧口径 teacher123 兜底)。
说明:探针全部断言「修复后应然行为」——修复前运行本脚本,上述标记项应显红(即 bug 实证)。
"""
import re
import requests

BASE = "http://localhost:8080/api"
results = []

def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)

def login(u, cands):
    for p in cands:
        r = requests.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=10).json()
        if r.get("code") == 0:
            return {"Authorization": "Bearer " + r["data"]["token"]}
    raise SystemExit("登录失败:" + u)

def post(h, path, body=None):
    return requests.post(BASE + path, headers=h, json=body if body is not None else {}, timeout=30).json()

def get(h, path, params=None):
    return requests.get(BASE + path, headers=h, params=params, timeout=30).json()

def detail(h, fid):
    return get(h, "/hx/%d" % fid)["data"]

def my_project(h, pid):
    for row in get(h, "/hx/my")["data"] or []:
        if row["projectId"] == pid:
            return row
    return None

def admin_project(pid):
    for row in get(A, "/hx/projects")["data"] or []:
        if row["projectId"] == pid:
            return row
    return None

def scan_upload(h, fid, doc_type, fname="scan.pdf"):
    return requests.post(BASE + "/hx/%d/scan" % fid, headers=h, data={"docType": doc_type},
                         files={"file": (fname, b"%PDF-1.4 fake scan")}, timeout=30).json()

def notify_titles(h, kw=None):
    out = []
    page = get(h, "/notify/my", {"current": 1, "size": 50}).get("data") or {}
    for n in page.get("records") or []:
        out.append(n["title"] if kw is None else (n["title"], n.get("content") or ""))
    return out

def rate_pct(h):
    r = get(h, "/config/hx_mgmt_fee_rate")
    try:
        return float((r.get("data") or "2"))
    except Exception:
        return 2.0

def money(v):
    return round(float(v), 2)

t = None; t2 = None; A = None   # 延迟到 main 里赋值

def main():
    global t, t2, A
    t = login("teacher", ["123", "teacher123"])
    t2 = login("teacher2", ["123", "teacher2123"])
    A = login("admin", ["admin123"])
    rate = rate_pct(t)

    # ============ Phase A 登记(teacher)============
    dto = {
        "name": "全流程-AI质检装备研发", "leaderName": "教师甲", "deptId": 1,
        "hx": {"projectType": "技术开发", "clientName": "全流程委托公司", "clientNature": "企业",
               "contractAmount": "355000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
               "useSchoolAsset": 0, "chargeMgmtFee": 1,
               "leaderEmail": "t@a.cn", "leaderPhone": "13800000001",
               "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [
            {"memberType": "教师", "employeeNo": "teacher", "name": "教师甲", "unit": "智能制造与装备学院",
             "title": "副教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1},
            {"memberType": "教师", "employeeNo": "teacher2", "name": "教师乙", "unit": "信息工程学院",
             "title": "讲师", "major": "软件工程", "duty": "算法开发", "sortNo": 2}],
        "budgets": [{"subject": "业务费", "amount": "80000.00"}],
    }
    r = post(t, "/hx/register/draft", dto)
    check("A1 保存登记草稿", r.get("code") == 0 and r["data"] > 0, str(r.get("msg")))
    fid = r["data"]; pid = detail(t, fid)["flow"]["projectId"]
    mfee = detail(t, fid)["hx"].get("mgmtFeeAmount")
    check("A2 管理费=比例换算", mfee is not None and abs(float(mfee) - 355000 * rate / 100) < 0.005,
          "rate=%s%% mgmt=%r" % (rate, mfee))
    r = post(t, "/hx/register/submit", {"flowId": fid})
    check("A3 预算不符拒提交(R1)", r.get("code") != 0 and "预算合计" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = post(t, "/hx/register/draft", dict(dto, flowId=fid,
           budgets=[{"subject": "业务费", "amount": "355000.00"}]))
    r = post(t, "/hx/register/submit", {"flowId": fid})
    check("A4 预算补齐提交 0→1", r.get("code") == 0 and detail(t, fid)["flow"]["status"] == 1,
          str(r.get("msg"))[:60])
    rows = [x for x in get(A, "/hx/audit", {"flowType": "register"})["data"] or [] if x["flowId"] == fid]
    check("A5 审批列表可见(状态1)", len(rows) == 1 and rows[0]["status"] == 1, str(rows)[:80])
    r = scan_upload(t, fid, "登记表")
    check("A6 预审前上传扫描件拒", r.get("code") != 0 and "预审" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = requests.post(BASE + "/hx/%d/scan" % fid, headers=t, data={"docType": "登记表"},
                      files={"file": ("x.txt", b"plain")}, timeout=30).json()
    check("A7 非法扩展名先拒(先于状态机)", r.get("code") != 0 and "格式" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = post(A, "/hx/%d/precheck" % fid)
    d = detail(t, fid)
    check("A8 预审通过 1→5", r.get("code") == 0 and d["flow"]["status"] == 5, str(r.get("msg"))[:60])
    check("A8b 时间线含 precheck", "precheck" in [x["action"] for x in d["records"]], "")
    r = scan_upload(t, fid, "登记表")
    check("A9 上传扫描件 5→2", r.get("code") == 0 and detail(t, fid)["flow"]["status"] == 2, "")
    r = post(t, "/hx/%d/withdraw" % fid)
    check("A10 撤回 2→5(重盖章免预审)", r.get("code") == 0 and detail(t, fid)["flow"]["status"] == 5, "")
    r = scan_upload(t, fid, "登记表", "scan2.pdf")
    d = detail(t, fid)
    scans = [a for a in d["attachments"] if a["bizType"] == "hx_scan"]
    check("A10b 重传覆盖,生效件恰1", r.get("code") == 0 and d["flow"]["status"] == 2 and len(scans) == 1, "")

    # C:退回意见超长(修复前=兜底系统异常)
    r = post(A, "/hx/%d/reject" % fid, {"opinion": "长" * 501, "isPrecheck": False})
    check("C 退回意见>500字友好拒", r.get("code") != 0 and "退回意见" in (r.get("msg") or "")
          and "超过" in (r.get("msg") or ""), str(r.get("msg"))[:60])
    r = post(A, "/hx/%d/reject" % fid, {"opinion": "金额与合同不符,请核对", "isPrecheck": False})
    check("A11 退回 2→4", r.get("code") == 0 and detail(t, fid)["flow"]["status"] == 4, str(r.get("msg"))[:60])
    r = post(t, "/hx/register/draft", dict(dto, flowId=fid, name="全流程-AI质检装备研发(修)",
           budgets=[{"subject": "业务费", "amount": "355000.00"}]))
    check("A12 退回态可编辑", r.get("code") == 0, str(r.get("msg"))[:60])
    r = post(t, "/hx/%d/resubmit" % fid)
    check("A13 重交 4→1", r.get("code") == 0 and detail(t, fid)["flow"]["status"] == 1, "")
    post(A, "/hx/%d/precheck" % fid)
    scan_upload(t, fid, "登记表", "scan3.pdf")
    r = post(A, "/hx/%d/review" % fid, {})
    d = detail(t, fid); ap = admin_project(pid)
    check("A14 审批通过 2→3+立项", r.get("code") == 0 and d["flow"]["status"] == 3
          and ap["auditStatus"] == 2 and ap["status"] == "立项" and ap["execStatus"] == "在研"
          and re.match(r"HX\d{4}-\d{3,}$", ap["approvalNo"] or ""), str(ap)[:120])
    r = post(t, "/hx/register/draft", dict(dto, flowId=fid, name="不应再改"))
    check("A15 通过后登记锁定", r.get("code") != 0 and "通过" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    check("A16 教师收到里程碑通知", any("登记确认通过" in x for x in notify_titles(t)),
          str(notify_titles(t))[:100])

    # ============ Phase A2 探针项目 P2(teacher)============
    p2 = dict(dto, name="边界探针P2", hx=dict(dto["hx"], contractAmount="9999999999999999"))
    r = post(t, "/hx/register/draft", p2)
    check("D 金额超 DECIMAL(14,2) 上限友好拒", r.get("code") != 0
          and ("最大" in (r.get("msg") or "") or "超过" in (r.get("msg") or ""))
          and "系统异常" not in (r.get("msg") or ""), str(r.get("msg"))[:60])
    # J2 比例保存级边界(二轮②):超999.99(库溢出)/超100/负数 一律友好拒(不建项目)
    for ratio, tag in [("12345.00", "超999.99"), ("150.00", "超100"), ("-5.00", "负数")]:
        r = post(t, "/hx/register/draft", dict(dto, name="比例边界" + tag,
               members=[{"memberType": "教师", "name": "甲", "ratio": ratio, "sortNo": 1}]))
        check("J2 比例%s保存拒(0~100)" % tag, r.get("code") != 0 and "比例" in (r.get("msg") or ""),
              str(r.get("msg"))[:40])
    r = post(t, "/hx/register/draft", dict(p2, hx=dict(dto["hx"])))
    check("A17 P2 草稿建立", r.get("code") == 0, str(r.get("msg"))[:60])
    fid2 = r["data"]
    r = post(t, "/hx/register/draft", dict(p2, flowId=fid2, name="边界探针P2",
           hx=dict(dto["hx"], contractAmount="100000.00",
                   startDate="2027-01-01", endDate="2026-01-01")))
    check("E 登记起止倒置拒", r.get("code") != 0 and "晚于" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = post(t, "/hx/change/draft", {"projectId": detail(t, fid2)["flow"]["projectId"],
           "changeType": "项目经费", "newAmount": "100000.00", "changeContent": "探针"})
    check("A18 P2 变更草稿建立", r.get("code") == 0, str(r.get("msg"))[:60])
    c2 = r["data"]
    pid2 = detail(t, fid2)["flow"]["projectId"]
    bal = [{"subject": "业务费", "amount": "355000.00"}]
    r = post(t, "/hx/register/draft", dict(dto, flowId=c2, name="越权改写", budgets=bal))
    d2 = detail(t, fid2)
    check("A1 借变更单id改登记数据被拒", r.get("code") != 0 and "登记" in (r.get("msg") or "")
          and d2["project"]["name"] == "边界探针P2",
          "code=%s name=%s" % (r.get("code"), d2["project"]["name"]))
    if r.get("code") == 0:   # 修复前自愈:恢复原名
        post(t, "/hx/register/draft", dict(p2, hx=dict(dto["hx"]), flowId=fid2, name="边界探针P2",
                                           budgets=bal))
    r = post(t, "/hx/register/submit", {"flowId": c2})
    ap2 = admin_project(pid2)
    check("A2b 借变更单id提交登记被拒", r.get("code") != 0 and "登记" in (r.get("msg") or "")
          and ap2["auditStatus"] == 0, "code=%s audit=%s" % (r.get("code"), ap2["auditStatus"]))
    post(t, "/hx/%d/cancel" % c2)                      # 清理 C2(0/1 两态均可取消)
    # J1 审批终态复检(二轮①):P2 配平提交→预审→5态改预算不等→回扫→审批须拒;自愈 2→5→1→0 删
    post(t, "/hx/register/draft", dict(dto, flowId=fid2, name="边界探针P2",
           budgets=[{"subject": "业务费", "amount": "355000.00"}]))
    post(t, "/hx/register/submit", {"flowId": fid2})
    post(A, "/hx/%d/precheck" % fid2)
    post(t, "/hx/register/draft", dict(dto, flowId=fid2, name="边界探针P2",
           budgets=[{"subject": "业务费", "amount": "1.00"}]))
    scan_upload(t, fid2, "登记表")
    r = post(A, "/hx/%d/review" % fid2, {})
    check("J1 预算漂移审批终检拒", r.get("code") != 0 and "预算合计" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    post(t, "/hx/%d/withdraw" % fid2)   # 2→5
    post(t, "/hx/%d/resubmit" % fid2)   # 5→1
    post(t, "/hx/%d/withdraw" % fid2)   # 1→0
    r = requests.delete(BASE + "/hx/%d" % fid2, headers=t, timeout=30).json()
    check("A19 P2 清理删除", r.get("code") == 0 and my_project(t, pid2) is None,
          str(r.get("msg"))[:60])

    # ============ Phase B 验收前置(R6)+到账 ============
    acc = {"projectId": pid, "usedAmount": "150000.00", "finishDate": "2027-12-20", "isOnTime": 1,
           "outcomeForm": "技术报告,专利", "acceptSummary": "按期完成全部指标",
           "reportContent": "一、项目概况\n\n二、成果", "members": None}
    r = post(t, "/hx/acceptance/draft", acc)
    check("B1 验收草稿建立", r.get("code") == 0, str(r.get("msg"))[:60])
    af = r["data"]
    r = post(t, "/hx/acceptance/submit", {"flowId": af})
    check("B2 无到账拒提交验收(R6)", r.get("code") != 0 and "到账" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = post(A, "/hx/funding", {"projectId": pid, "amount": "200000.00", "receivedDate": "2026-05-20",
           "payer": "全流程委托公司", "bankFlowNo": "FL-001"})
    check("B3 到账录入", r.get("code") == 0, str(r.get("msg"))[:60])
    fd = get(t, "/hx/%d/funding" % pid)["data"]
    check("B4 教师到账只读=200000", money(fd["total"]) == 200000.0 and len(fd["records"]) == 1, str(fd)[:100])
    r = get(t2, "/hx/%d/funding" % pid)
    check("B5 他人项目到账拒", r.get("code") != 0, str(r.get("msg"))[:40])

    # ============ Phase C 变更 ============
    r = post(t, "/hx/change/draft", {"projectId": pid, "changeType": "项目经费",
           "newAmount": "410000.00", "changeContent": "追加开发范围", "changeReason": "委托方追加"})
    cf1 = r["data"]
    r = post(t, "/hx/change/submit", {"flowId": cf1})
    check("C1 变更#1提交 0→1", r.get("code") == 0, str(r.get("msg"))[:60])
    r = post(t, "/hx/change/draft", {"projectId": pid, "changeType": "项目经费",
           "newAmount": "420000.00", "changeContent": "二单"})
    check("C2 在途变更唯一拒", r.get("code") != 0 and "进行中" in (r.get("msg") or ""), str(r.get("msg"))[:60])
    r = post(A, "/hx/%d/reject" % cf1, {"opinion": "经费变更须附补充协议", "isPrecheck": True})
    d = detail(t, cf1)
    check("C3 预审退回 1→4(precheck_reject)", r.get("code") == 0 and d["flow"]["status"] == 4
          and "precheck_reject" in [x["action"] for x in d["records"]], "")
    r = post(t, "/hx/change/draft", {"flowId": cf1, "projectId": pid, "changeType": "项目经费",
           "newAmount": "405000.00", "changeContent": "追加开发范围(修)", "changeReason": "委托方追加"})
    check("C4 退回态变更可编辑", r.get("code") == 0, str(r.get("msg"))[:60])
    r = post(t, "/hx/%d/resubmit" % cf1)
    check("C5 变更重交 4→1", r.get("code") == 0, str(r.get("msg"))[:60])
    r = post(t, "/hx/%d/cancel" % cf1)
    gone = cf1 not in [x["flowId"] for x in get(A, "/hx/audit", {"flowType": "change"})["data"]]
    check("C6 取消在途变更(1态)", r.get("code") == 0 and gone, "")
    r = post(t, "/hx/change/draft", {"projectId": pid, "changeType": "执行周期",
           "newEndDate": "2024-01-01", "changeContent": "探针"})
    check("E2 变更结束早于开始拒", r.get("code") != 0 and "早于" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = post(t, "/hx/change/draft", {"projectId": pid, "changeType": "执行周期",
           "newEndDate": "2025-06-30", "changeContent": "探针2"})
    # 修复前自愈:两支探针若建单成功一律取消,防在途唯一挡住后续正式变更
    for f in ((my_project(t, pid) or {}).get("flows") or []):
        if f["flowType"] == "change" and f["status"] == 0:
            post(t, "/hx/%d/cancel" % f["flowId"])
    lead = [x for x in get(t, "/hx/leaders", {"q": "teacher2"})["data"] or [] if x["username"] == "teacher2"]
    check("C7 负责人搜索", len(lead) == 1, str(lead)[:60])
    uid2 = lead[0]["id"]
    r = post(t, "/hx/change/draft", {"projectId": pid, "changeType": "项目经费,执行周期,项目负责人",
           "newAmount": "400000.00", "newEndDate": "2028-06-30", "newLeaderId": uid2,
           "changeContent": "追加经费+延期+易主", "changeReason": "原负责人出国交流"})
    cf3 = r["data"]
    post(t, "/hx/change/submit", {"flowId": cf3})
    post(A, "/hx/%d/precheck" % cf3)
    scan_upload(t, cf3, "变更表")
    r = post(A, "/hx/%d/review" % cf3, {})
    d = detail(t2, cf3)   # 易主生效,原负责人已无权访问
    hx = d["hx"]
    check("C8 变更生效(经费/周期/易主)", r.get("code") == 0 and d["flow"]["status"] == 3
          and money(hx["contractAmount"]) == 400000.0
          and abs(float(hx["mgmtFeeAmount"]) - 400000 * rate / 100) < 0.005
          and (hx["endDate"] or "")[:10] == "2028-06-30"
          and my_project(t, pid) is None and my_project(t2, pid) is not None,
          str({k: hx.get(k) for k in ("contractAmount", "mgmtFeeAmount", "endDate")}))
    check("C8b beforeJson 快照在", bool(d["flow"].get("beforeJson")), str(d["flow"].get("beforeJson"))[:60])
    r = get(t, "/hx/%d" % af)
    check("C9 易主后原负责人失权", r.get("code") != 0, str(r.get("msg"))[:40])

    # ============ Phase D 验收(teacher2)============
    mem_bad_ratio = [dict(x) for x in detail(t2, af)["members"]]
    for m, ratio in zip(mem_bad_ratio, ["40.00", "50.00"]):
        m["ratio"] = ratio
    r = post(t2, "/hx/acceptance/draft", dict(acc, flowId=af, members=mem_bad_ratio))
    r = post(t2, "/hx/acceptance/submit", {"flowId": af})
    check("D1 比例合计≠100拒(R3)", r.get("code") != 0 and "比例" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    mem_neg = [dict(x) for x in detail(t2, af)["members"]]
    for m, ratio, amount in zip(mem_neg, ["-50.00", "150.00"], ["80000.00", "120000.00"]):
        m["ratio"] = ratio; m["amount"] = amount
    r = post(t2, "/hx/acceptance/draft", dict(acc, flowId=af, members=mem_neg))
    check("D2 成员比例越界拒(保存级0~100)", r.get("code") != 0 and "比例" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    mem_bad_amt = [dict(x) for x in detail(t2, af)["members"]]
    for m, ratio, amount in zip(mem_bad_amt, ["40.00", "60.00"], ["80000.00", "110000.00"]):
        m["ratio"] = ratio; m["amount"] = amount
    r = post(t2, "/hx/acceptance/draft", dict(acc, flowId=af, members=mem_bad_amt))
    r = post(t2, "/hx/acceptance/submit", {"flowId": af})
    check("D3 成员金额合计≠到账拒(R4)", r.get("code") != 0 and "金额合计" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    mem_ok = [dict(x) for x in detail(t2, af)["members"]]
    for m, ratio, amount in zip(mem_ok, ["40.00", "60.00"], ["80000.00", "120000.00"]):
        m["ratio"] = ratio; m["amount"] = amount
    r = post(t2, "/hx/acceptance/draft", dict(acc, flowId=af, usedAmount="250000.00", members=mem_ok))
    r = post(t2, "/hx/acceptance/submit", {"flowId": af})
    check("D4 使用经费>到账拒(R5)", r.get("code") != 0 and "不得超过" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    # ③ 在途变更时禁提验收(2026-11-05 拍板:变更须先办结,验收基于变更后口径)
    cf_p = post(t2, "/hx/change/draft", {"projectId": pid, "changeType": "项目经费",
                "newAmount": "405000.00", "changeContent": "在途变更(③探针)"})["data"]
    post(t2, "/hx/change/submit", {"flowId": cf_p})
    r = post(t2, "/hx/acceptance/submit", {"flowId": af})
    check("③ 在途变更时提交验收拒", r.get("code") != 0 and "变更单" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    post(t2, "/hx/%d/cancel" % cf_p)
    r = post(t2, "/hx/acceptance/draft", dict(acc, flowId=af, members=mem_ok))
    r = post(t2, "/hx/acceptance/submit", {"flowId": af})
    check("D5 验收提交 0→1", r.get("code") == 0 and detail(t2, af)["flow"]["status"] == 1, str(r.get("msg"))[:60])
    # ② 竞态窗口探针:验收在途时插入一张变更并推到已回扫,验收先结题后该变更须批不动
    cf4 = post(t2, "/hx/change/draft", {"projectId": pid, "changeType": "项目经费",
               "newAmount": "410000.00", "changeContent": "与验收并行(②探针)"})["data"]
    post(t2, "/hx/change/submit", {"flowId": cf4})
    post(A, "/hx/%d/precheck" % cf4)
    scan_upload(t2, cf4, "变更表")
    post(A, "/hx/%d/precheck" % af)
    scan_upload(t2, af, "验收登记表")
    check("D6 验收登记表扫描件 5→2", detail(t2, af)["flow"]["status"] == 2, "")
    r = post(A, "/hx/%d/review" % af, {"verifiedAmount": "200000.00"})
    concluded = detail(t2, af)["flow"]["status"] == 3
    check("B 验收件不全拒批(缺验收报告)", r.get("code") != 0 and "不全" in (r.get("msg") or "")
          and not concluded, str(r.get("msg"))[:60])
    if not concluded:
        scan_upload(t2, af, "验收报告")
        # J3 审批终态复检-到账漂移(二轮①):删到账→R6/R4 口径漂移,审批须拒;复录后批过
        rec1 = get(t2, "/hx/%d/funding" % pid)["data"]["records"][0]["id"]
        requests.delete(BASE + "/hx/funding/%d" % rec1, headers=A, timeout=30)
        r = post(A, "/hx/%d/review" % af, {"verifiedAmount": "200000.00"})
        check("J3 删到账口径漂移审批终检拒", r.get("code") != 0 and "到账" in (r.get("msg") or ""),
              str(r.get("msg"))[:60])
        post(A, "/hx/funding", {"projectId": pid, "amount": "200000.00",
             "receivedDate": "2026-05-20", "payer": "全流程委托公司"})
        r = post(A, "/hx/%d/review" % af, {"verifiedAmount": "200000.00"})
    d = detail(t2, af); ap = admin_project(pid)
    check("D7 验收通过结题+核定额", d["flow"]["status"] == 3 and ap["status"] == "结题"
          and ap["execStatus"] == "已结题" and money(d["hx"]["verifiedAmount"]) == 200000.0,
          str({k: ap.get(k) for k in ("status", "execStatus")}))
    # ② 结题后批在途变更拒(2026-11-05 拍板:竞态窗口封口),随后 admin 退回→教师取消清理
    r = post(A, "/hx/%d/review" % cf4, {})
    check("② 结题后批变更拒", r.get("code") != 0 and "结题" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    post(A, "/hx/%d/reject" % cf4, {"opinion": "项目已结题,变更不再受理", "isPrecheck": False})
    post(t2, "/hx/%d/cancel" % cf4)
    r = get(t2, "/hx/%d/funding" % pid)
    check("D8 结题后教师到账仍可读", r.get("code") == 0, str(r.get("msg"))[:40])

    # ============ Phase E 结题后边界 ============
    r = post(t2, "/hx/change/draft", {"projectId": pid, "changeType": "项目经费",
           "newAmount": "410000.00", "changeContent": "结题后再变更"})
    check("F 结题项目禁发变更", r.get("code") != 0 and "结题" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    if r.get("code") == 0:   # 修复前自愈:提交路径一并实证后取消
        r2 = post(t2, "/hx/change/submit", {"flowId": r["data"]})
        print("      [F-Submit探针] 修复前结题提交变更 code=%s" % r2.get("code"))
        post(t2, "/hx/%d/cancel" % r["data"])
    r = post(A, "/hx/funding", {"projectId": pid, "amount": "100.999", "receivedDate": "2027-01-10"})
    fd = get(A, "/hx/funding", {"projectId": pid})["data"]
    note = [c for ti, c in notify_titles(t2, 1) if ti == "横向项目到账通知" and "2027-01-10" in c]
    check("G 到账金额两位小数入库+通知同值", r.get("code") == 0
          and money(fd["records"][-1]["amount"]) == 101.0
          and note and "100.999" not in note[-1] and "101.00" in note[-1],
          "record=%s note=%s" % (fd["records"][-1]["amount"], note[-1][-40:] if note else ""))
    rec_id = fd["records"][-1]["id"]
    r = requests.delete(BASE + "/hx/funding/%d" % rec_id, headers=A, timeout=30).json()
    check("E1 探针到账核销清理", r.get("code") == 0, str(r.get("msg"))[:40])
    titles_t = notify_titles(t)
    titles_t2 = notify_titles(t2)
    check("E2 教师甲通知链齐全", any("单被退回" in x for x in titles_t)
          and any("到账通知" in x for x in titles_t) and any("变更生效" in x for x in titles_t),
          str(titles_t)[:120])
    check("E3 教师乙通知链齐全(易主+验收)", any("变更生效" in x for x in titles_t2)
          and any("验收通过" in x for x in titles_t2), str(titles_t2)[:120])

    # ============ 汇总 ============
    bad = [n for n, ok in results if not ok]
    print("\n==== 共 %d 项,通过 %d,失败 %d ====" % (len(results), len(results) - len(bad), len(bad)))
    for n in bad:
        print("  FAIL:", n)

if __name__ == "__main__":
    main()
