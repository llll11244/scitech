# -*- coding: utf-8 -*-
"""
科技处审批接口冒烟(N4 Task24):审批列表/三流程通过副作用(登记编号入账/变更改写/验收结题+终版两张)/
退回(预检+常规)/教师重交(旧待签+旧扫描件作废)/越权与幂等。
Task28 补强:before_json camelCase 键齐全(diff 展示数据完整性)/同一登记并发通过两连发恰一成功/
  /hx/leaders 收紧(viewer 拒+LIKE 通配转义,viewer 号脚本内经 admin 建号,收尾停用)。
本脚本首次贯通 N3 卡住的全链路:登记确认(audit_status=2)→变更/验收提交→通过生效(spec §6.1)。
前置:应用已启动(dev),四类模板启用版在库,LibreOffice 就位;admin/admin123、teacher/teacher123、
teacher2/teacher2123(dev 种子)。
"""
import json
import re
import sys
import threading
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
    # 2026-10-24 测试教师密码直改库为 123;兼容旧口径 teacher123/teacher2123 回退
    for pwd in (p, "123", u + "123"):
        r = requests.post(BASE + "/auth/login", json={"username": u, "password": pwd}, timeout=10).json()
        if r.get("code") == 0:
            return {"Authorization": "Bearer " + r["data"]["token"]}
    raise SystemExit("登录失败:" + u)


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


def q_leaders(headers, q):
    """leaders 搜索(params 编码,% 等特殊字符原样送达服务端)"""
    return requests.get(BASE + "/hx/leaders", headers=headers, params={"q": q}, timeout=30).json()


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
               "useSchoolAsset": 0, "chargeMgmtFee": 1,
               "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "employeeNo": "20190001", "name": "教师甲",
                      "unit": "智能制造与装备学院",
                      "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "355000.00"}],
    }
    fid_a = post("/hx/register/draft", t, reg_a)["data"]
    assert post("/hx/register/submit", t, {"flowId": fid_a}).get("code") == 0, "A 登记提交失败"
    # 预审流转(四轮反馈):1 态 scan 拒;precheck 1→5 后可传;草稿不进审批列表
    r = scan(t, fid_a, "登记表")
    check("A 预审前上传扫描件被拒", r.get("code") != 0 and "预审" in (r.get("msg") or ""), str(r.get("msg"))[:60])
    r = post("/hx/%d/precheck" % fid_a, admin, {})
    check("A 预审通过(1→5)", r.get("code") == 0, str(r.get("msg"))[:60])
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

    # 1b. 预审流转(四轮反馈):草稿不进审批列表;5 态教师改数据后 resubmit 5→1 重新预审
    reg_d = dict(reg_a, name="审批测试-草稿不可见演练")
    fid_d = post("/hx/register/draft", t, reg_d)["data"]
    r = get("/hx/audit?flowType=register", admin)
    ids = [x.get("flowId") for x in (r.get("data") or [])]
    check("草稿不进审批列表", fid_d not in ids, "fid_d=%s" % fid_d)
    assert post("/hx/register/submit", t, {"flowId": fid_d}).get("code") == 0, "D 登记提交失败"
    r = get("/hx/audit?flowType=register", admin)
    ids = [x.get("flowId") for x in (r.get("data") or [])]
    check("提交后进审批列表", fid_d in ids, "fid_d=%s" % fid_d)
    assert post("/hx/%d/precheck" % fid_d, admin, {}).get("code") == 0, "D 预审失败"
    r = post("/hx/register/draft", t, dict(reg_d, flowId=fid_d, name="审批测试-草稿不可见演练(改)"))
    d5 = detail(fid_d, t)
    check("预审通过态(5)教师可改数据", r.get("code") == 0 and d5["project"]["name"].endswith("(改)")
          and d5["flow"]["status"] == 5, "status=%s" % d5["flow"]["status"])
    r = post("/hx/%d/resubmit" % fid_d, t, {})
    check("5 态重新提交回预审(5→1)", r.get("code") == 0 and detail(fid_d, t)["flow"]["status"] == 1,
          str(r.get("msg"))[:60])
    r = post("/hx/%d/resubmit" % fid_d, t, {})
    check("1 态重复 resubmit 拒", r.get("code") != 0, str(r.get("msg"))[:40])
    requests.post(BASE + "/hx/%d/withdraw" % fid_d, headers=t)

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
    # 3b. Task28 补强:diff 展示数据完整性——before_json 五字段 camelCase 键齐全+值正确
    #     (N4 审批 diff 按键消费,缺键即空白对照;MySQL JSON 列读回已规范化)
    bj = json.loads(d["flow"]["beforeJson"])
    check("before_json camelCase 键齐全",
          set(bj) == {"name", "leaderName", "deptId", "contractAmount", "endDate"}
          and bj["name"] == reg_a["name"] and bj["leaderName"] == "教师甲"
          and bj["deptId"] == 9 and dec(bj["contractAmount"]) == Decimal("355000.00")
          and str(bj["endDate"]) == "2027-12-31",
          str(bj)[:140])
    assert post("/hx/%d/precheck" % cfid, admin, {}).get("code") == 0, "A 变更预审失败"
    assert scan(t, cfid, "变更表").get("code") == 0, "A 变更回扫失败"
    r = post("/hx/%d/review" % cfid, admin, {})
    d = detail(cfid, admin)
    check("变更通过三改写", r.get("code") == 0 and d["flow"]["status"] == 3
          and dec(d["hx"]["contractAmount"]) == Decimal("420000.00")
          and str(d["hx"]["endDate"]) == "2027-06-30"
          and d["project"]["applyUserId"] == t2_id and d["project"]["leaderName"] == "测试教师乙",
          "amount=%s end=%s owner=%s leader=%s" % (d["hx"]["contractAmount"], d["hx"]["endDate"],
                                                    d["project"]["applyUserId"], d["project"]["leaderName"]))
    # 3c. 管理费随经费重算:登记 355000×2%=7100 → 变更通过 420000×2%=8400(比例归科技处配置)
    check("变更通过管理费随经费重算", dec(d["hx"]["mgmtFeeAmount"]) == Decimal("8400.00"),
          "mgmtFeeAmount=%s" % d["hx"].get("mgmtFeeAmount"))
    check("变更不改台账状态/通知新负责人", d["project"]["auditStatus"] == 2 and d["project"]["status"] == "立项"
          and notify_has(t2, pid_a, "变更"),
          "audit=%s status=%s" % (d["project"]["auditStatus"], d["project"]["status"]))

    # 4. 验收(项目已易主 teacher2):金额核算 R3-R6 负例 → 录到账 → 配平提交 → 核定结题
    acc = {"projectId": pid_a, "usedAmount": "410000.00", "finishDate": "2027-12-20", "isOnTime": 1,
           "outcomeForm": "技术报告,专利", "acceptSummary": "全部委托内容已完成并验收",
           "reportContent": "一、项目目标达成情况\n\n按合同完成全部改造内容并上线运行。\n\n二、经费使用情况\n\n使用经费41万元。",
           "members": [{"memberType": "教师", "name": "教师乙", "unit": "智能制造与装备学院",
                        "title": "讲师", "major": "自动化", "duty": "系统联调",
                        "ratio": "100.00", "amount": "420000.00", "sortNo": 1}]}
    afid = post("/hx/acceptance/draft", t2, acc)["data"]
    # 4a. R6:无到账记录拒提交(到账=科技处录入,funding 尚无该项目的款项)
    r = post("/hx/acceptance/submit", t2, {"flowId": afid})
    check("验收无到账拒提交(R6)", r.get("code") != 0 and "到账" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    # 4b. 录到账 420000(=变更后合同经费)
    r = post("/hx/funding", admin, {"projectId": pid_a, "amount": "420000.00",
                                    "receivedDate": "2027-12-01", "payer": "审批测试委托单位",
                                    "remark": "验收核算前置"})
    assert r.get("code") == 0, "4b 前置:录到账失败:" + str(r)
    # 4c. R3:比例合计≠100 拒;R4:金额合计≠到账拒;R5:使用>到账拒
    post("/hx/acceptance/draft", t2, dict(acc, flowId=afid,
        members=[dict(acc["members"][0], ratio="80.00", amount="420000.00")]))
    r = post("/hx/acceptance/submit", t2, {"flowId": afid})
    check("成员比例合计≠100拒(R3)", r.get("code") != 0 and "比例" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    post("/hx/acceptance/draft", t2, dict(acc, flowId=afid,
        members=[dict(acc["members"][0], ratio="100.00", amount="410000.00")]))
    r = post("/hx/acceptance/submit", t2, {"flowId": afid})
    check("成员金额合计≠到账拒(R4)", r.get("code") != 0 and "到账" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    post("/hx/acceptance/draft", t2, dict(acc, flowId=afid, usedAmount="430000.00"))
    r = post("/hx/acceptance/submit", t2, {"flowId": afid})
    check("使用经费>到账拒(R5)", r.get("code") != 0 and "不得超过" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    # 4d. 配平(比例100/金额=到账/使用≤到账)→提交通过;admin 代改验收单留痕(决策③)
    assert post("/hx/acceptance/draft", t2, dict(acc, flowId=afid)).get("code") == 0, "4d 配平重存失败"
    assert post("/hx/acceptance/submit", t2, {"flowId": afid}).get("code") == 0, "A 验收提交失败"
    r = post("/hx/%d/admin-save" % afid, admin,
             dict(acc, flowId=afid, acceptSummary="科技处修正:全部委托内容已完成并验收(修订)"))
    d = detail(afid, admin)
    check("admin代改验收单留痕", r.get("code") == 0 and "admin_edit" in [x["action"] for x in d["records"]]
          and "修订" in (d["flow"].get("acceptSummary") or ""), str(r.get("msg"))[:60])
    assert post("/hx/%d/precheck" % afid, admin, {}).get("code") == 0, "A 验收预审失败"
    assert scan(t2, afid, "验收登记表").get("code") == 0, "A 验收回扫失败"
    # 2026-11-05 审核边界B:验收审批须两件盖章件齐全,测试夹具补传验收报告
    assert scan(t2, afid, "验收报告").get("code") == 0, "A 验收回扫(报告)失败"
    r = post("/hx/%d/review" % afid, admin, {})   # 缺 verifiedAmount → 必拦
    check("验收核定金额必填", r.get("code") != 0 and "必填" in (r.get("msg") or ""), str(r.get("msg")))
    # 终审修复:负数/0 拒(与 addFunding 正数校验对称,核定额入台账+渲进终版归档件须先拦)
    r = post("/hx/%d/review" % afid, admin, {"verifiedAmount": -1.00})
    check("验收核定额负数拒", r.get("code") != 0 and "大于 0" in (r.get("msg") or ""), str(r.get("msg")))
    r = post("/hx/%d/review" % afid, admin, {"verifiedAmount": 0})
    check("验收核定额 0 拒", r.get("code") != 0 and "大于 0" in (r.get("msg") or ""), str(r.get("msg")))
    r = post("/hx/%d/review" % afid, admin, {"verifiedAmount": 415000.00})
    d = detail(afid, admin)
    p = d["project"]
    finals = sorted(a["bizType"] for a in eff_atts(d, afid)
                    if a["bizType"].startswith("hx_final"))
    check("验收通过结题+核定额", r.get("code") == 0 and d["flow"]["status"] == 3
          and p["status"] == "结题" and str(p.get("finishDate")) == str(date.today())
          and dec(d["hx"]["verifiedAmount"]) == Decimal("415000.00"),
          "status=%s finish=%s verified=%s" % (p["status"], p.get("finishDate"), d["hx"]["verifiedAmount"]))
    # HTML转正(决策①):不再渲终版 docx 归档件——通过后打印只读页(带核定额/核定人)即终版
    check("通过后无终版渲染件(HTML即交付)", finals == [], str(finals))
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
    check("重交回已提交(无渲染附件)", r.get("code") == 0 and d["flow"]["status"] == 1
          and "resubmit" in [x["action"] for x in d["records"]]
          and len(eff_atts(d, fid_b, "hx_docx")) == 0, "status=%s" % d["flow"]["status"])

    # 8. 预审→回扫→常规退回(status=2)→重交:旧扫描件一并作废(spec §3.2)
    assert post("/hx/%d/precheck" % fid_b, admin, {}).get("code") == 0, "B 预审失败"
    assert scan(t, fid_b, "登记表").get("code") == 0, "B 回扫失败"
    d = detail(fid_b, t)
    assert len(eff_atts(d, fid_b, "hx_scan")) == 1, "前置:生效扫描件应为 1"
    r = post("/hx/%d/reject" % fid_b, admin, {"opinion": "盖章页缺页,补章后重传"})
    d = detail(fid_b, t)
    check("常规退回(status2)", r.get("code") == 0 and d["flow"]["status"] == 4
          and "reject" in [x["action"] for x in d["records"]], "status=%s" % d["flow"]["status"])
    r = post("/hx/%d/resubmit" % fid_b, t, {})
    d = detail(fid_b, t)
    check("重交作废旧扫描件(HTML即交付,无重渲)", r.get("code") == 0 and d["flow"]["status"] == 1
          and len(eff_atts(d, fid_b, "hx_scan")) == 0,
          "scan=%d" % len(eff_atts(d, fid_b, "hx_scan")))

    # 9. 重新预审→回扫→通过:编号与 A 唯一且格式合法
    assert post("/hx/%d/precheck" % fid_b, admin, {}).get("code") == 0, "B 二次预审失败"
    assert scan(t, fid_b, "登记表").get("code") == 0, "B 二次回扫失败"
    r = post("/hx/%d/review" % fid_b, admin, {})
    d = detail(fid_b, admin)
    no_a = detail(fid_a, admin)["project"]["approvalNo"]
    no_b = d["project"]["approvalNo"]
    check("通过且编号唯一合法", r.get("code") == 0 and d["flow"]["status"] == 3
          and no_re.match(no_b or "") and no_a != no_b, "A=%s B=%s" % (no_a, no_b))

    # ---------------- 项目C(teacher):并发通过两连发(Task28 补强,决策#34-② 条件更新守卫) ----------------
    reg_c = dict(reg_a, name="审批测试-并发通过演练项目")
    fid_c = post("/hx/register/draft", t, reg_c)["data"]
    assert post("/hx/register/submit", t, {"flowId": fid_c}).get("code") == 0, "C 登记提交失败"
    assert post("/hx/%d/precheck" % fid_c, admin, {}).get("code") == 0, "C 预审失败"
    assert scan(t, fid_c, "登记表").get("code") == 0, "C 回扫失败"

    # 9b. 两线程同时 approve 同一登记:恰好一个成功(编号只分配一次),另一个可观测失败
    barrier = threading.Barrier(2)
    outcomes = []

    def hit():
        barrier.wait(timeout=10)
        outcomes.append(post("/hx/%d/review" % fid_c, admin, {}))

    threads = [threading.Thread(target=hit) for _ in range(2)]
    for th in threads:
        th.start()
    for th in threads:
        th.join(timeout=60)
    d = detail(fid_c, admin)
    ok_cnt = sum(1 for x in outcomes if x.get("code") == 0)
    fail_msgs = [str(x.get("msg")) for x in outcomes if x.get("code") != 0]
    check("并发通过两连发恰一成功",
          len(outcomes) == 2 and ok_cnt == 1 and len(fail_msgs) == 1
          and any(kw in fail_msgs[0] for kw in ("状态已变化", "已回扫"))
          and d["flow"]["status"] == 3 and no_re.match(d["project"].get("approvalNo") or ""),
          "codes=%s msgs=%s no=%s" % ([x.get("code") for x in outcomes], fail_msgs,
                                      d["project"].get("approvalNo")))

    # 10. 越权:教师不可调 admin 审批接口
    check("教师不可通过/退回/列表", post("/hx/%d/review" % fid_b, t, {}).get("code") != 0
          and post("/hx/%d/reject" % fid_b, t, {"opinion": "x"}).get("code") != 0
          and get("/hx/audit", t).get("code") != 0, "")

    # 11. Task28 收紧:/hx/leaders viewer 拒 + LIKE 通配转义(teacher/admin 可用=变更表单依赖)
    r = post("/users", admin, {"username": "t28viewer", "realName": "只读访客二十八",
                               "role": "viewer", "password": "Viewer123"})
    if r.get("code") != 0:
        # 可重跑:历史 t28viewer(改密/停用)→ 重置口令+启用后继续
        stale = next((x for x in (get("/users?q=t28viewer", admin).get("data") or {}).get("records") or []
                      if x.get("username") == "t28viewer"), None)
        assert stale, "viewer 建号失败且无历史账号:" + str(r.get("msg"))
        vid = stale["id"]
        requests.put(BASE + "/users/%s/password" % vid, headers=admin,
                     json={"password": "Viewer123"}, timeout=10)
        requests.put(BASE + "/users/%s/status" % vid, headers=admin,
                     json={"status": 1}, timeout=10)
    else:
        vid = r.get("data")
    v = login("t28viewer", "Viewer123")
    check("leaders viewer 拒", q_leaders(v, "t").get("code") != 0, "")
    check("leaders LIKE 转义(%/_ 不当通配)",
          q_leaders(t, "%").get("data") == [] and q_leaders(t, "_").get("data") == []
          and q_leaders(t, "te_cher").get("data") == [],
          "%%=%d _=%d te_cher=%d" % (len(q_leaders(t, "%").get("data") or []),
                                     len(q_leaders(t, "_").get("data") or []),
                                     len(q_leaders(t, "te_cher").get("data") or [])))
    check("leaders 正常搜索不受影响",
          any(u.get("username") == "teacher2" for u in (q_leaders(t, "teacher2").get("data") or [])),
          "")
    # 收尾:停用 viewer 测试号(留存停用态,与 t26user 同口径)
    requests.put(BASE + "/users/%s/status" % vid, headers=admin, json={"status": 0}, timeout=10)

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
