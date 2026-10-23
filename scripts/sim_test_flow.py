# -*- coding: utf-8 -*-
"""
单项目全流程模拟(N6 口径):一个测试项目走完三条流程覆盖四张表单——
  register 登记 → change 变更 → acceptance 验收(验收单+验收报告)。
按「草稿/提交/回扫/审批」最小步执行(用户逐小步确认):--stage <step>;状态存 scripts/.sim_flow_state.json。
扫描件统一用 --scan 指定 PDF 模拟盖章回扫件(默认桌面样例)。
N6 口径:HTML 即交付(后端不渲 docx,提交后无渲染附件);回扫/审批不变;
  验收前须 admin 先录到账(R6);R1 预算合计=合同经费;R3 比例合计=100;R4 成员金额合计=到账;R5 使用≤到账。
"""
import argparse
import json
import os
import sys
from datetime import date

import requests

BASE = "http://localhost:8080/api"
STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".sim_flow_state.json")
DEFAULT_SCAN = r"C:\Users\LJY\Desktop\internshipPlan!print.pdf"

REG = {
    "name": "流程模拟-纺织智能检测装备研发", "leaderName": "测试教师", "deptId": 9,
    "hx": {"projectType": "技术开发", "clientName": "模拟委托纺织科技有限公司", "clientNature": "企业",
           "contractAmount": "355000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
           "useSchoolAsset": 0, "chargeMgmtFee": 1,
           "leaderEmail": "teacher@example.edu.cn",
           "projectSummary": "面向织造场景的纱线疵点在线检测:视觉方案设计、检测算法与上位机软件开发、样机试制与产线联调。",
           "progressPlan": "2026上半年完成方案与算法开发;下半年样机调试;2027年产线联调并验收结题。"},
    "members": [
        {"memberType": "教师", "name": "测试教师", "unit": "智能制造与装备学院", "title": "副教授",
         "major": "机械工程", "duty": "总体设计", "sortNo": 1},
        {"memberType": "教师", "name": "测试教师乙", "unit": "信息工程学院", "title": "讲师",
         "major": "控制工程", "duty": "检测算法", "sortNo": 2},
    ],
    "budgets": [  # R1:合计=355000
        {"subject": "业务费", "amount": "150000.00"},
        {"subject": "劳务费", "amount": "100000.00"},
        {"subject": "协作费", "amount": "80000.00"},
        {"subject": "其它", "amount": "25000.00"},
    ],
}

CHG_NEW_AMOUNT = "420000.00"   # 变更:经费调增
CHG_NEW_END = "2028-06-30"     # 变更:延期半年
ACC_USED = "410000.00"         # 验收:使用经费(≤到账 420000)

results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)
    if not ok:
        sys.exit("步骤中断:" + name + " " + str(detail)[:300])


def login(u, p):
    r = requests.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=10).json()
    assert r.get("code") == 0, "登录失败 %s:%s" % (u, r.get("msg"))
    return {"Authorization": "Bearer " + r["data"]["token"]}


def post(path, headers, body):
    return requests.post(BASE + path, headers=headers, json=body, timeout=120).json()


def detail(fid, headers):
    return requests.get(BASE + "/hx/%d" % fid, headers=headers, timeout=30).json()["data"]


def scan(headers, fid, doc_type, path):
    with open(path, "rb") as f:
        return requests.post(BASE + "/hx/%d/scan" % fid, headers=headers,
                             data={"docType": doc_type},
                             files={"file": ("scan.pdf", f)}, timeout=60).json()


def load_state():
    if not os.path.exists(STATE_FILE):
        sys.exit("状态文件不存在,先跑 register-draft")
    with open(STATE_FILE, encoding="utf-8") as f:
        return json.load(f)


def save_state(**kw):
    st = load_state() if os.path.exists(STATE_FILE) else {}
    st.update(kw)
    with open(STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(st, f, ensure_ascii=False, indent=1)
    return st


def need(st, key):
    if not st.get(key):
        sys.exit("状态缺 %s,先跑对应草稿步" % key)
    return st[key]


# ---------------- 登记(register)----------------

def register_draft(args):
    t = login("teacher", "teacher123")
    fid = post("/hx/register/draft", t, REG)["data"]
    check("保存登记草稿", fid > 0, "flowId=%s" % fid)
    save_state(registerFlowId=fid)
    print("[登记草稿已存] flowId=%s;下一步 register-submit(教师端我的项目可看草稿)" % fid)


def register_submit(args):
    st = load_state()
    fid = need(st, "registerFlowId")
    t = login("teacher", "teacher123")
    r = post("/hx/register/submit", t, {"flowId": fid})
    d = detail(fid, t)
    check("登记提交", r.get("code") == 0 and d["flow"]["status"] == 1, "status=%s" % d["flow"]["status"])
    check("无渲染附件(HTML即交付)", all(not a["bizType"].startswith("hx_docx") for a in d["attachments"]))
    print("[已提交待签] 打印页 http://localhost:5173/print/register/%s;下一步 register-scan(线下盖章后回扫)" % fid)


def register_scan(args):
    st = load_state()
    fid = need(st, "registerFlowId")
    t = login("teacher", "teacher123")
    r = scan(t, fid, "登记表", args.scan)
    d = detail(fid, t)
    check("登记回扫", r.get("code") == 0 and d["flow"]["status"] == 2, "status=%s" % d["flow"]["status"])
    print("[已回扫待审] 下一步 register-review")


def register_review(args):
    st = load_state()
    fid = need(st, "registerFlowId")
    admin = login("admin", "admin123")
    r = post("/hx/%d/review" % fid, admin, {})
    d = detail(fid, admin)
    p = d["project"]
    check("登记审批立项", r.get("code") == 0 and d["flow"]["status"] == 3
          and p["status"] == "立项" and p.get("approvalNo"),
          "status=%s 编号=%s 确认日=%s" % (d["flow"]["status"], p.get("approvalNo"), d["hx"].get("hxRegisterDate")))
    check("管理费后端换算(2%)", abs(float(d["hx"]["mgmtFeeAmount"]) - 7100.0) < 0.005,
          "mgmtFeeAmount=%s" % d["hx"].get("mgmtFeeAmount"))
    save_state(projectId=p["id"], contractAmount=REG["hx"]["contractAmount"])
    print("[登记结案] projectId=%s;下一步 change-draft(或验收 acceptance-funding)" % p["id"])


# ---------------- 变更(change)----------------

def _chg_body():
    return {"projectId": need(load_state(), "projectId"), "changeType": "项目经费,执行周期",
            "changeContent": "委托方追加一台样机需求,经费调增至 42 万元;整体延期至 2028-06-30",
            "changeReason": "委托方追加订单并协商延长执行周期",
            "newAmount": CHG_NEW_AMOUNT, "newEndDate": CHG_NEW_END}


def change_draft(args):
    st = load_state()
    t = login("teacher", "teacher123")
    cfid = post("/hx/change/draft", t, _chg_body())["data"]
    check("保存变更草稿", cfid > 0, "flowId=%s" % cfid)
    save_state(changeFlowId=cfid)
    print("[变更草稿已存] flowId=%s;下一步 change-submit" % cfid)


def change_submit(args):
    st = load_state()
    cfid = need(st, "changeFlowId")
    t = login("teacher", "teacher123")
    r = post("/hx/change/submit", t, {"flowId": cfid})
    d = detail(cfid, t)
    check("变更提交", r.get("code") == 0 and d["flow"]["status"] == 1, "status=%s" % d["flow"]["status"])
    check("原信息快照已存", "355000" in (d["flow"].get("beforeJson") or ""),
          str(d["flow"].get("beforeJson"))[:100])
    print("[已提交待签] 打印页 http://localhost:5173/print/change/%s;下一步 change-scan" % cfid)


def change_scan(args):
    st = load_state()
    cfid = need(st, "changeFlowId")
    t = login("teacher", "teacher123")
    r = scan(t, cfid, "变更表", args.scan)
    d = detail(cfid, t)
    check("变更回扫", r.get("code") == 0 and d["flow"]["status"] == 2, "status=%s" % d["flow"]["status"])
    print("[已回扫待审] 下一步 change-review")


def change_review(args):
    st = load_state()
    cfid = need(st, "changeFlowId")
    admin = login("admin", "admin123")
    r = post("/hx/%d/review" % cfid, admin, {})
    d = detail(cfid, admin)
    check("变更审批通过(改写生效)", r.get("code") == 0 and d["flow"]["status"] == 3
          and float(d["hx"]["contractAmount"]) == float(CHG_NEW_AMOUNT)
          and str(d["hx"]["endDate"]) == CHG_NEW_END,
          "经费=%s 周期至=%s" % (d["hx"]["contractAmount"], d["hx"]["endDate"]))
    check("管理费随经费重算", abs(float(d["hx"]["mgmtFeeAmount"]) - float(CHG_NEW_AMOUNT) * 0.02) < 0.005,
          "mgmtFeeAmount=%s" % d["hx"].get("mgmtFeeAmount"))
    save_state(contractAmount=CHG_NEW_AMOUNT)
    print("[变更结案] 下一步 acceptance-funding")


# ---------------- 验收(acceptance)----------------

def _acc_body():
    return {"projectId": need(load_state(), "projectId"), "usedAmount": ACC_USED,
            "finishDate": date.today().isoformat(), "isOnTime": 1,
            "outcomeForm": "技术报告,专利",
            "acceptSummary": "完成纱线疵点在线检测样机与上位机软件,识别准确率 97%,已在委托方产线上线运行,通过双方联合验收。",
            "reportContent": "一、项目目标达成情况\n\n按合同完成全部约定内容:检测样机、上位机软件 1 套,申请发明专利 1 项。\n\n二、经费使用情况\n\n合同额 42 万元,实际使用 41 万元,主要为样机物料、外协加工与测试费用。\n\n三、成果与后续\n\n装备已在委托方产线连续运行,双方同意进入二期合作。",
            "members": [  # R3 比例合计=100;R4 金额合计=到账 420000
                {"memberType": "教师", "name": "测试教师", "unit": "智能制造与装备学院", "title": "副教授",
                 "major": "机械工程", "duty": "总体设计", "ratio": "60.00", "amount": "252000.00", "sortNo": 1},
                {"memberType": "教师", "name": "测试教师乙", "unit": "信息工程学院", "title": "讲师",
                 "major": "控制工程", "duty": "检测算法", "ratio": "40.00", "amount": "168000.00", "sortNo": 2},
            ]}


def acceptance_funding(args):
    st = load_state()
    total = need(st, "contractAmount")
    admin = login("admin", "admin123")
    r = post("/hx/funding", admin, {"projectId": st["projectId"], "amount": total,
                                    "receivedDate": date.today().isoformat(),
                                    "payer": REG["hx"]["clientName"], "remark": "验收核算前置(全款)"})
    check("录入到账 %s(R6 前置)" % total, r.get("code") == 0, str(r.get("msg"))[:80])
    print("[到账已录] 下一步 acceptance-draft")


def acceptance_draft(args):
    t = login("teacher", "teacher123")
    afid = post("/hx/acceptance/draft", t, _acc_body())["data"]
    check("保存验收草稿", afid > 0, "flowId=%s" % afid)
    save_state(acceptanceFlowId=afid)
    print("[验收草稿已存] flowId=%s;下一步 acceptance-submit" % afid)


def acceptance_submit(args):
    st = load_state()
    afid = need(st, "acceptanceFlowId")
    t = login("teacher", "teacher123")
    r = post("/hx/acceptance/submit", t, {"flowId": afid})
    d = detail(afid, t)
    check("验收提交(R3-R5 校验过)", r.get("code") == 0 and d["flow"]["status"] == 1,
          "status=%s" % d["flow"]["status"])
    print("[已提交待签] 验收单打印页 http://localhost:5173/print/acceptance/%s" % afid)
    print("                验收报告打印页 http://localhost:5173/print/acceptance/%s?doc=hx_report" % afid)
    print("                下一步 acceptance-scan")


def acceptance_scan(args):
    st = load_state()
    afid = need(st, "acceptanceFlowId")
    t = login("teacher", "teacher123")
    r = scan(t, afid, "验收登记表", args.scan)
    d = detail(afid, t)
    check("验收回扫", r.get("code") == 0 and d["flow"]["status"] == 2, "status=%s" % d["flow"]["status"])
    print("[已回扫待审] 下一步 acceptance-review")


def acceptance_review(args):
    st = load_state()
    afid = need(st, "acceptanceFlowId")
    admin = login("admin", "admin123")
    r = post("/hx/%d/review" % afid, admin, {"verifiedAmount": float(ACC_USED)})
    d = detail(afid, admin)
    p = d["project"]
    check("验收审批结题+核定额", r.get("code") == 0 and d["flow"]["status"] == 3 and p["status"] == "结题"
          and float(d["hx"]["verifiedAmount"]) == float(ACC_USED),
          "status=%s 核定=%s 结题日=%s" % (p["status"], d["hx"].get("verifiedAmount"), p.get("finishDate")))
    save_state(verifiedAmount=ACC_USED)
    print("[全流程结题] 四张表单走完:登记/变更/验收单/验收报告")


STEPS = {
    "register-draft": register_draft, "register-submit": register_submit,
    "register-scan": register_scan, "register-review": register_review,
    "change-draft": change_draft, "change-submit": change_submit,
    "change-scan": change_scan, "change-review": change_review,
    "acceptance-funding": acceptance_funding, "acceptance-draft": acceptance_draft,
    "acceptance-submit": acceptance_submit, "acceptance-scan": acceptance_scan,
    "acceptance-review": acceptance_review,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--stage", required=True, choices=sorted(STEPS))
    ap.add_argument("--scan", default=DEFAULT_SCAN)
    args = ap.parse_args()
    if not os.path.exists(args.scan):
        sys.exit("扫描件不存在:" + args.scan)
    STEPS[args.stage](args)
    print("%d/%d 项过" % (len(results), len(results)))


if __name__ == "__main__":
    main()
