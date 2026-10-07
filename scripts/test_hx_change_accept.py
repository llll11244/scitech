# -*- coding: utf-8 -*-
"""
变更+验收流程接口冒烟(N3 Task21):draft 保存/分屏预览 docx/提交"未确认"拦截/必填联动/在途唯一防线下越权。
SKIP 说明:N3 无科技处确认接口,project.audit_status 到不了 2,变更/验收 submit 只能测"未确认被拒";
  全迁移(确认→提交→验收双单渲染→回扫)在 N4 审批接口就绪后由 test_hx_audit.py 覆盖。
前置:应用已启动(dev),四类模板启用版在库,LibreOffice 就位;teacher/teacher123 为本人。
"""
import io
import sys
import zipfile

import requests

BASE = "http://localhost:8080/api"
results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login(u, p):
    r = requests.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=10).json()
    return {"Authorization": "Bearer " + r["data"]["token"]}


def post(path, headers, body, timeout=30):
    """POST→dict;非 JSON 响应(如 404 空体)回 code=-1 供统一断言"""
    try:
        return requests.post(BASE + path, headers=headers, json=body, timeout=timeout).json()
    except ValueError:
        return {"code": -1, "msg": "non-json response"}


def preview_ok(resp):
    """docx 预览流校验:HTTP 200 + zip 魔数 + document.xml 无 {{ 残留"""
    if resp.status_code != 200 or resp.content[:2] != b"PK":
        return False
    with zipfile.ZipFile(io.BytesIO(resp.content)) as z:
        return "{{" not in z.read("word/document.xml").decode("utf-8", "ignore")


def main():
    t = login("teacher", "teacher123")
    print("SKIP - 变更/验收全迁移(确认→提交→双单渲染→回扫)在 N4 审批接口就绪后由 test_hx_audit.py 覆盖;"
          "本脚本只测未确认拦截与 draft/preview 全链(办法 C,计划 Task21 Step1)")

    # 前置:建登记草稿并提交(flow=register status=1,project.audit_status=1)
    reg = {
        "name": "接口测试-变更验收用产线改造", "leaderName": "教师甲", "deptId": 1,
        "hx": {"projectType": "技术开发", "clientName": "测试委托公司", "clientNature": "企业",
               "contractAmount": "355000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
               "useSchoolAsset": 0, "chargeMgmtFee": 1, "mgmtFeeAmount": "10000.00",
               "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                      "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "80000.00"}],
    }
    r = post("/hx/register/draft", t, reg, timeout=120)
    assert r.get("code") == 0, "前置登记草稿失败:" + str(r)
    fid = r["data"]
    r = post("/hx/register/submit", t, {"flowId": fid}, timeout=120)
    assert r.get("code") == 0, "前置登记提交失败:" + str(r)
    d = requests.get(BASE + "/hx/%d" % fid, headers=t, timeout=30).json()["data"]
    pid = d["project"]["id"]
    assert d["flow"]["status"] == 1 and d["project"]["auditStatus"] == 1, "前置状态异常"

    # 1. change/draft 保存(项目本人,audit_status=1 允许起草——draft 不校验确认态,提交才校验)
    chg = {"projectId": pid, "changeType": "委托内容",
           "changeContent": "增加一条产线的数据采集与看板模块", "changeReason": "委托方业务扩展",
           "newAmount": None, "newEndDate": None, "newLeaderId": None}
    r = post("/hx/change/draft", t, chg)
    check("变更草稿保存", r.get("code") == 0 and isinstance(r.get("data"), int) and r["data"] > 0,
          str(r.get("data") if r.get("code") == 0 else r.get("msg")))
    cfid = r.get("data")

    # 2. change/preview docx(zip 魔数+无 {{ 残留)
    pv = requests.post(BASE + "/hx/preview", headers=t,
                       json={"flowType": "change", "data": dict(chg, flowId=cfid)}, timeout=60)
    check("变更分屏预览 docx", preview_ok(pv), "%d bytes" % len(pv.content))

    # 3. change/submit → 期望拒绝(audit_status=1 未确认)
    r = post("/hx/change/submit", t, {"flowId": cfid})
    check("变更提交未确认被拒", r.get("code") != 0 and "未确认" in (r.get("msg") or ""), str(r.get("msg")))

    # 4. change 必填联动:changeType 含"项目经费"但 newAmount 空 → draft 拒绝
    r = post("/hx/change/draft", t, dict(chg, flowId=cfid, changeType="项目经费,委托内容"))
    check("变更必填联动拦截", r.get("code") != 0, str(r.get("msg")))

    # 4b. 编辑路径空值显式置空(终审 F1,MP NOT_NULL 策略):先存含"项目经费"草稿,
    #     再改存纯"委托内容"草稿,详情 newAmount 须为空(旧实现 updateById 残留 360000)
    r = post("/hx/change/draft", t, dict(chg, flowId=cfid, changeType="项目经费",
                                         newAmount="360000.00", newEndDate=None, newLeaderId=None))
    assert r.get("code") == 0, "F1 前置:含项目经费草稿保存失败:" + str(r)
    d = requests.get(BASE + "/hx/%d" % cfid, headers=t, timeout=30).json()["data"]
    had = d["flow"].get("newAmount") is not None
    r = post("/hx/change/draft", t, dict(chg, flowId=cfid, changeType="委托内容",
                                         newAmount=None, newEndDate=None, newLeaderId=None))
    assert r.get("code") == 0, "F1 前置:改存纯委托内容草稿失败:" + str(r)
    d = requests.get(BASE + "/hx/%d" % cfid, headers=t, timeout=30).json()["data"]
    check("变更草稿编辑空值显式置空", had and d["flow"].get("newAmount") is None,
          "newAmount=%r" % d["flow"].get("newAmount"))

    # 5. acceptance/draft 保存(is_on_time/outcome_form/成员 ratio+amount/学生/report_content)
    acc = {"projectId": pid, "usedAmount": "348000.00", "isOnTime": 1,
           "outcomeForm": "技术报告,专利", "acceptSummary": "全部委托内容已完成并验收",
           "reportContent": "一、项目目标达成情况\n\n【正文】按合同完成全部改造内容并上线运行。\n\n二、经费使用情况\n\n使用经费34.80万元,执行率98%。",
           "members": [
               {"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                "title": "教授", "major": "机械工程", "duty": "总体设计",
                "ratio": "60.00", "amount": "213000.00", "sortNo": 1},
               {"memberType": "学生", "name": "赵同学", "studentNo": "2023011234",
                "major": "机电一体化", "mentor": "教师甲", "duty": "数据采集开发", "sortNo": 2}]}
    r = post("/hx/acceptance/draft", t, acc)
    check("验收草稿保存", r.get("code") == 0 and isinstance(r.get("data"), int) and r["data"] > 0,
          str(r.get("data") if r.get("code") == 0 else r.get("msg")))
    afid = r.get("data")

    # 6. acceptance/preview 两类模板(hx_acceptance+hx_report)各验 zip 魔数+无 {{
    pa = requests.post(BASE + "/hx/preview", headers=t,
                       json={"flowType": "acceptance", "tplType": "hx_acceptance",
                             "data": dict(acc, flowId=afid)}, timeout=60)
    pr = requests.post(BASE + "/hx/preview", headers=t,
                       json={"flowType": "acceptance", "tplType": "hx_report",
                             "data": dict(acc, flowId=afid)}, timeout=60)
    check("验收分屏预览两张模板", preview_ok(pa) and preview_ok(pr),
          "acceptance=%dB report=%dB" % (len(pa.content), len(pr.content)))

    # 6b. 验收编辑路径空值显式置空(终审 F1 同款):改存草稿清空 isOnTime,详情须为空
    r = post("/hx/acceptance/draft", t, dict(acc, flowId=afid, isOnTime=None))
    assert r.get("code") == 0, "F1 前置:验收草稿改存失败:" + str(r)
    d = requests.get(BASE + "/hx/%d" % afid, headers=t, timeout=30).json()["data"]
    check("验收草稿编辑空值显式置空", d["flow"].get("isOnTime") is None,
          "isOnTime=%r" % d["flow"].get("isOnTime"))

    # 7. acceptance/submit → 期望拒绝(未确认)
    r = post("/hx/acceptance/submit", t, {"flowId": afid})
    check("验收提交未确认被拒", r.get("code") != 0 and "未确认" in (r.get("msg") or ""), str(r.get("msg")))

    # 8. 越权:teacher 对不存在项目提 change → 拒绝
    r = post("/hx/change/draft", t, {"projectId": 99999999, "changeType": "委托内容",
                                     "changeContent": "x", "changeReason": "y"})
    check("不存在项目被拒", r.get("code") != 0, str(r.get("msg")))

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
