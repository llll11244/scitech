# -*- coding: utf-8 -*-
"""
变更+验收流程接口冒烟(N3 Task21;HTML转正 2026-10-21 改版):draft 保存/必填联动/字数硬校验/
工号与不受限标志持久化回显/提交"未确认"拦截/在途唯一防线下越权。
docx 分屏预览与待签渲染已随「HTML 即交付」退役(决策①),相关用例改为数据层断言;
全迁移(确认→提交→回扫→审批)由 test_hx_audit.py 覆盖。
前置:应用已启动(dev);teacher/teacher123 为本人。
"""
import sys

import requests

BASE = "http://localhost:8080/api"
results = []


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


def post(path, headers, body, timeout=30):
    """POST→dict;非 JSON 响应(如 404 空体)回 code=-1 供统一断言"""
    try:
        return requests.post(BASE + path, headers=headers, json=body, timeout=timeout).json()
    except ValueError:
        return {"code": -1, "msg": "non-json response"}


def main():
    t = login("teacher", "teacher123")

    # 前置:建登记草稿并提交(flow=register status=1,project.audit_status=1)
    # HTML转正:预算合计=合同经费(规则R1)才可提交;成员工号/不受限标志新列随 T1 落库
    reg = {
        "name": "接口测试-变更验收用产线改造", "leaderName": "教师甲", "deptId": 1,
        "hx": {"projectType": "技术开发", "clientName": "测试委托公司", "clientNature": "企业",
               "contractAmount": "355000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
               "useSchoolAsset": 0, "chargeMgmtFee": 1,
               "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "employeeNo": "20190001", "name": "教师甲",
                      "unit": "智能制造与装备学院",
                      "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "355000.00"}],
    }
    r = post("/hx/register/draft", t, reg, timeout=120)
    assert r.get("code") == 0, "前置登记草稿失败:" + str(r)
    fid = r["data"]

    # 0a/HTML转正 T1:工号/不受限标志持久化回显(登记详情)
    d = requests.get(BASE + "/hx/%d" % fid, headers=t, timeout=30).json()["data"]
    check("成员工号+预算不受限标志回显",
          (d["members"] or [{}])[0].get("employeeNo") == "20190001"
          and (d["budgets"] or [{}])[0].get("unrestricted") == 0,
          "employeeNo=%r unrestricted=%r" % ((d["members"] or [{}])[0].get("employeeNo"),
                                             (d["budgets"] or [{}])[0].get("unrestricted")))

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

    # 2/HTML转正 T4:字数硬校验——变更内容>500 拒保存
    r = post("/hx/change/draft", t, dict(chg, flowId=cfid, changeContent="长" * 501))
    check("变更内容超 500 字拒保存", r.get("code") != 0 and "超过" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])

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

    # 5. acceptance/draft 保存(is_on_time/outcome_form/finish_date/成员 ratio+amount/学生/report_content)
    acc = {"projectId": pid, "usedAmount": "348000.00", "finishDate": "2027-12-20", "isOnTime": 1,
           "outcomeForm": "技术报告,专利", "acceptSummary": "全部委托内容已完成并验收",
           "reportContent": "一、项目目标达成情况\n\n【正文】按合同完成全部改造内容并上线运行。\n\n二、经费使用情况\n\n使用经费34.80万元,执行率98%。",
           "members": [
               {"memberType": "教师", "employeeNo": "20190001", "name": "教师甲",
                "unit": "智能制造与装备学院",
                "title": "教授", "major": "机械工程", "duty": "总体设计",
                "ratio": "60.00", "amount": "213000.00", "sortNo": 1},
               {"memberType": "学生", "name": "赵同学", "studentNo": "2023011234",
                "major": "机电一体化", "mentor": "教师甲", "duty": "数据采集开发", "sortNo": 2}]}
    r = post("/hx/acceptance/draft", t, acc)
    check("验收草稿保存", r.get("code") == 0 and isinstance(r.get("data"), int) and r["data"] > 0,
          str(r.get("data") if r.get("code") == 0 else r.get("msg")))
    afid = r.get("data")

    # 6/HTML转正 T1:验收「完成时间」(rp_hx_flow.finish_date 教师填报列)持久化回显
    d = requests.get(BASE + "/hx/%d" % afid, headers=t, timeout=30).json()["data"]
    check("验收完成时间回显", str(d["flow"].get("finishDate") or "")[:10] == "2027-12-20",
          str(d["flow"].get("finishDate")))

    # 6b. 验收编辑路径空值显式置空(终审 F1 同款):改存草稿清空 isOnTime,详情须为空
    r = post("/hx/acceptance/draft", t, dict(acc, flowId=afid, isOnTime=None))
    assert r.get("code") == 0, "F1 前置:验收草稿改存失败:" + str(r)
    d = requests.get(BASE + "/hx/%d" % afid, headers=t, timeout=30).json()["data"]
    check("验收草稿编辑空值显式置空", d["flow"].get("isOnTime") is None,
          "isOnTime=%r" % d["flow"].get("isOnTime"))

    # 6c/Task28(N3 终审挂账):members=null(字段未传)=不动成员表;显式空列表=清空(与登记同口径)
    r = post("/hx/acceptance/draft", t, dict(acc, flowId=afid, members=None))
    d = requests.get(BASE + "/hx/%d" % afid, headers=t, timeout=30).json()["data"]
    check("验收编辑不传成员=成员表不动", r.get("code") == 0 and len(d["members"]) == 2,
          "members=%d" % len(d["members"]))
    r = post("/hx/acceptance/draft", t, dict(acc, flowId=afid, members=[]))
    d = requests.get(BASE + "/hx/%d" % afid, headers=t, timeout=30).json()["data"]
    check("验收编辑显式空成员=清空", r.get("code") == 0 and len(d["members"]) == 0,
          "members=%d" % len(d["members"]))

    # 7. acceptance/submit → 期望拒绝(未确认)
    r = post("/hx/acceptance/submit", t, {"flowId": afid})
    check("验收提交未确认被拒", r.get("code") != 0 and "未确认" in (r.get("msg") or ""), str(r.get("msg")))

    # 8. 越权:teacher 对不存在项目提 change → 拒绝
    r = post("/hx/change/draft", t, {"projectId": 99999999, "changeType": "委托内容",
                                     "changeContent": "x", "changeReason": "y"})
    check("不存在项目被拒", r.get("code") != 0, str(r.get("msg")))

    # 9. 取消变更(五轮反馈):在途变更可终止;作废后唯一约束解除可再发新变更;审核中(2)拒
    admin = login("admin", "admin123")
    fake = b"%PDF-1.4 fake scan"

    def scan(headers, fid, doc_type):
        return requests.post(BASE + "/hx/%d/scan" % fid, headers=headers,
                             data={"docType": doc_type}, files={"file": ("scan.pdf", fake)}).json()

    # 前置:登记走完立项(预审→回扫→通过)
    assert post("/hx/%d/precheck" % fid, admin, {}).get("code") == 0, "取消段:登记预审失败"
    assert scan(t, fid, "登记表").get("code") == 0, "取消段:登记回扫失败"
    assert post("/hx/%d/review" % fid, admin, {}).get("code") == 0, "取消段:登记审批失败"

    # 第一张变更单(cfid,委托内容)提交后取消 → 详情不可见、可再发新变更
    assert post("/hx/change/draft", t, dict(chg, flowId=cfid)).get("code") == 0, "取消段:变更重存失败"
    assert post("/hx/change/submit", t, {"flowId": cfid}).get("code") == 0, "取消段:变更提交失败"
    r = post("/hx/%d/cancel" % cfid, t, {})
    check("在途变更取消成功", r.get("code") == 0, str(r.get("msg"))[:60])
    r = requests.get(BASE + "/hx/%d" % cfid, headers=t, timeout=30).json()
    check("取消后流程单不可见", r.get("code") != 0, str(r.get("code")))
    r = post("/hx/change/draft", t, {"projectId": pid, "changeType": "委托内容",
                                     "changeContent": "取消后重新发起", "changeReason": "重新规划"})
    check("取消后可再发新变更", r.get("code") == 0, str(r.get("msg"))[:60])
    cfid2 = r.get("data")
    assert post("/hx/change/submit", t, {"flowId": cfid2}).get("code") == 0, "取消段:新变更提交失败"
    assert post("/hx/%d/precheck" % cfid2, admin, {}).get("code") == 0, "取消段:新变更预审失败"
    assert scan(t, cfid2, "变更表").get("code") == 0, "取消段:新变更回扫失败"
    r = post("/hx/%d/cancel" % cfid2, t, {})
    check("审核中(2)取消被拒", r.get("code") != 0 and "撤回" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    # 收尾:新变更撤回到待盖章后取消,恢复项目干净态
    requests.post(BASE + "/hx/%d/withdraw" % cfid2, headers=t)
    assert post("/hx/%d/cancel" % cfid2, t, {}).get("code") == 0, "取消段:收尾取消失败"

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
