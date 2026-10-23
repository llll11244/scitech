# -*- coding: utf-8 -*-
"""
登记流程接口冒烟(N3 Task20;HTML转正 2026-10-21 改版):状态机教师侧全迁移+金额/字数硬校验+
工号查人+审批前可编辑(1/2 态)+admin 代改留痕+通过后锁定。
待签 docx 双出/分屏预览已随「HTML 即交付」退役(决策①)——提交后无 hx_docx/hx_pdf 附件,
待签件=教师浏览器打印;扫描件上传/审批流程不变。
前置:应用已启动(dev);teacher/teacher123 为本人,teacher2/teacher2123 为 dev 种子(越权用例)。
"""
import requests

BASE = "http://localhost:8080/api"
results = []

def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)

def login(u, p):
    r = requests.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=10).json()
    return {"Authorization": "Bearer " + r["data"]["token"]}

def detail(fid, headers):
    return requests.get(BASE + "/hx/%d" % fid, headers=headers, timeout=30).json()["data"]

def main():
    t = login("teacher", "teacher123")
    admin = login("admin", "admin123")

    # 1. 草稿保存(项目+成员+预算一次提交;预算故意不等合同经费,先测 R1 再改齐)
    dto = {
        "name": "接口测试-智能化产线改造", "leaderName": "教师甲", "deptId": 1,
        "hx": {"projectType": "技术开发", "clientName": "测试委托公司", "clientNature": "企业",
                "contractAmount": "355000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
                "useSchoolAsset": 0, "chargeMgmtFee": 1,
                "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "employeeNo": "20190001", "name": "教师甲",
                      "unit": "智能制造与装备学院",
                      "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "80000.00"}],
    }
    r = requests.post(BASE + "/hx/register/draft", headers=t, json=dto).json()
    check("保存草稿", r.get("code") == 0 and r["data"] > 0, str(r.get("data")))
    fid = r["data"]

    # 1a. 管理费金额后端换算:按科技处配置比例 2% × 355000 = 7100.00
    d = detail(fid, t)
    mfee = d["hx"].get("mgmtFeeAmount")
    check("管理费金额=配置比例换算", mfee is not None and abs(float(mfee) - 7100.0) < 0.005,
          "mgmtFeeAmount=%r" % mfee)

    # 1b. 规则R1:预算合计(80000)≠合同经费(355000)→ draft 可存半成品但提交拒
    r = requests.post(BASE + "/hx/register/submit", headers=t, json={"flowId": fid}).json()
    check("预算合计不符拒提交(R1)", r.get("code") != 0 and "预算合计" in (r.get("msg") or ""),
          str(r.get("msg"))[:80])

    # 1c. 规则R2:不受限科目金额归零落库、不计入合计(业务费不受限+劳务费335000→合计仍≠355000)
    r = requests.post(BASE + "/hx/register/draft", headers=t, json=dict(dto, flowId=fid,
        budgets=[{"subject": "业务费", "amount": "80000.00", "unrestricted": 1},
                 {"subject": "劳务费", "amount": "335000.00"}])).json()
    d = detail(fid, t)
    bmap = {b["subject"]: b for b in d["budgets"]}
    check("不受限科目金额归零落库(R2)",
          r.get("code") == 0 and bmap["业务费"]["amount"] in (0, 0.0, "0.00")
          and bmap["业务费"].get("unrestricted") == 1,
          "业务费=%r/%r" % (bmap["业务费"]["amount"], bmap["业务费"].get("unrestricted")))
    r = requests.post(BASE + "/hx/register/submit", headers=t, json={"flowId": fid}).json()
    check("不受限科目不计入合计(R1)", r.get("code") != 0, str(r.get("msg"))[:60])

    # 1d. 规则T4:字数硬校验——简介>800 拒保存
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid, hx=dict(dto["hx"], projectSummary="长" * 801))).json()
    check("简介超 800 字拒保存", r.get("code") != 0 and "超过" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])

    # 1e. 预算补齐(业务费=355000)→ 提交通过
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid, budgets=[{"subject": "业务费", "amount": "355000.00"}])).json()
    r = requests.post(BASE + "/hx/register/submit", headers=t, json={"flowId": fid}).json()
    check("预算补齐后提交", r.get("code") == 0, str(r.get("msg")))
    d = detail(fid, t)
    check("状态=1已提交", d["flow"]["status"] == 1, str(d["flow"]["status"]))
    # 决策①:不再生成待签 docx/pdf 附件(列表为空,待签件=浏览器打印)
    biz = sorted(a["bizType"] for a in d["attachments"])
    check("提交后无待签渲染附件(HTML即交付)", len(biz) == 0, str(biz))

    # 2. 工号查人(T2):命中 teacher2 / 查无返 null
    r = requests.get(BASE + "/hx/member-lookup", headers=t, params={"q": "teacher2"}, timeout=10).json()
    m = r.get("data") or {}
    check("工号查人命中", r.get("code") == 0 and m.get("username") == "teacher2"
          and bool(m.get("name")), str(m)[:80])
    r = requests.get(BASE + "/hx/member-lookup", headers=t, params={"q": "zzz99999"}, timeout=10).json()
    check("工号查无返null", r.get("code") == 0 and r.get("data") is None, str(r.get("data")))

    # 3. 撤回 1→0 → 再提交(幂等,附件仍无)
    requests.post(BASE + "/hx/%d/withdraw" % fid, headers=t)
    d = detail(fid, t)
    check("撤回回草稿", d["flow"]["status"] == 0, str(d["flow"]["status"]))
    r = requests.post(BASE + "/hx/register/submit", headers=t, json={"flowId": fid}).json()
    check("重新提交", r.get("code") == 0)

    # 4. 回扫上传(假 PDF 字节)→2;重传覆盖:生效件仍恰 1(旧件作废不入列表,设计§3.2)
    fake = b"%PDF-1.4 fake scan"
    # 预审流转(四轮反馈):1 态拒——预审通过后方可上传;precheck(1→5)后可传
    r = requests.post(BASE + "/hx/%d/scan" % fid, headers=t, data={"docType": "登记表"},
                      files={"file": ("scan.pdf", fake)}).json()
    check("预审前上传扫描件被拒", r.get("code") != 0 and "预审" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = requests.post(BASE + "/hx/%d/precheck" % fid, headers=admin, json={}).json()
    d = detail(fid, t)
    check("科技处预审通过(1→5)", r.get("code") == 0 and d["flow"]["status"] == 5,
          "status=%s" % d["flow"]["status"])
    check("时间线含 precheck", "precheck" in [x["action"] for x in d["records"]], "")
    r = requests.post(BASE + "/hx/%d/scan" % fid, headers=t, data={"docType": "登记表"},
                      files={"file": ("scan.pdf", fake)}).json()
    check("预审后上传扫描件", r.get("code") == 0, str(r))
    r2 = requests.post(BASE + "/hx/%d/scan" % fid, headers=t, data={"docType": "登记表"},
                      files={"file": ("scan2.pdf", fake)}).json()
    d = detail(fid, t)
    scans = [a for a in d["attachments"] if a["bizType"] == "hx_scan"]
    check("重传覆盖旧件作废", r2.get("code") == 0 and d["flow"]["status"] == 2 and len(scans) == 1,
          "生效扫描件=%d" % len(scans))

    # 5. 已回扫(2)教师仍可改数据(决策②:审批前可改,编辑不迁移状态不作废扫描件)
    #    sleep 1.2s:滞后判定按 DATETIME 秒级精度,跨秒才能观测 update_time>scan_time
    import time
    time.sleep(1.2)
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid, name="接口测试-智能化产线改造(改)",
                                budgets=[{"subject": "业务费", "amount": "355000.00"}])).json()
    d = detail(fid, t)
    check("已回扫态教师可改数据", r.get("code") == 0 and d["project"]["name"].endswith("(改)")
          and d["flow"]["status"] == 2, "status=%s name=%s" % (d["flow"]["status"], d["project"]["name"]))

    # 5b. 滞后判定数据:回扫后数据被改 → flow.updateTime > scanTime(前端据此提示重新打印)
    check("改动时间晚于回扫时间(滞后判定)",
          str(d["flow"].get("updateTime") or "") > str(d["flow"].get("scanTime") or ""),
          "update=%s scan=%s" % (d["flow"].get("updateTime"), d["flow"].get("scanTime")))

    # 6. 时间线含 submit/withdraw/upload_scan 动作(撤回留痕=ACT_WITHDRAW)
    acts = [x["action"] for x in d["records"]]
    check("时间线动作", "submit" in acts and "upload_scan" in acts and "withdraw" in acts, str(acts))

    # 7. admin 代改(决策③:{1,2} 任意字段):改委托单位;留痕 admin_edit;教师调用拒
    body = dict(dto, flowId=fid, name=d["project"]["name"],
                hx=dict(dto["hx"], clientName="科技处修正委托单位"),
                budgets=[{"subject": "业务费", "amount": "355000.00"}])
    r = requests.post(BASE + "/hx/%d/admin-save" % fid, headers=admin, json=body).json()
    d2 = detail(fid, admin)
    acts = [x["action"] for x in d2["records"]]
    check("admin代改任意字段", r.get("code") == 0 and d2["hx"]["clientName"] == "科技处修正委托单位",
          str(r.get("msg")))
    check("admin代改留痕admin_edit", "admin_edit" in acts, str(acts))
    r = requests.post(BASE + "/hx/%d/admin-save" % fid, headers=t, json=body).json()
    check("admin代改教师调用拒", r.get("code") != 0, str(r.get("msg"))[:40])

    # 8. admin 审批通过 2→3 → 教师编辑锁定 + admin 代改锁定(决策②③:通过后不可改)
    r = requests.post(BASE + "/hx/%d/review" % fid, headers=admin, json={}).json()
    d = detail(fid, admin)
    check("审批通过", r.get("code") == 0 and d["flow"]["status"] == 3, str(r.get("msg")))
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid, budgets=[{"subject": "业务费", "amount": "355000.00"}])).json()
    check("通过后教师编辑锁定", r.get("code") != 0, str(r.get("msg"))[:40])
    r = requests.post(BASE + "/hx/%d/admin-save" % fid, headers=admin, json=body).json()
    check("通过后admin代改锁定", r.get("code") != 0, str(r.get("msg"))[:40])

    # 9. 越权与状态防御(teacher2 为 dev 种子)
    r = requests.get(BASE + "/hx/%d" % (fid + 99999), headers=t)
    check("不存在流程被拒", r.json().get("code") != 0, str(r.status_code))
    r = requests.get(BASE + "/hx/%d" % fid, headers=login("teacher2", "teacher2123"))
    check("教师间越权被拒", r.json().get("code") != 0, str(r.json().get("code")))

    # 10. 我的列表含本项目
    my = requests.get(BASE + "/hx/my", headers=t).json()["data"]
    check("我的项目列表", any(x.get("flowId") == fid or x.get("projectId") for x in my), str(len(my)))

    # 11. 删草稿(新建草稿→删,连带成员预算附件逻辑删)
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, budgets=[{"subject": "业务费", "amount": "355000.00"}])).json()
    fid2 = r["data"]

    # 11a/终审补充(同 F1,MP NOT_NULL 策略):草稿带管理费保存→编辑清空可选字段重存→
    #     详情 mgmtFeeAmount/leaderEmail 须为空(charge=0 换算为 null)
    dto3 = dict(dto, flowId=fid2,
                hx=dict(dto["hx"], chargeMgmtFee=0, leaderEmail=None,
                        projectSummary=None, progressPlan=None),
                budgets=[{"subject": "业务费", "amount": "355000.00"}])
    r = requests.post(BASE + "/hx/register/draft", headers=t, json=dto3).json()
    assert r.get("code") == 0, "终审补充前置:编辑清空重存失败:" + str(r)
    d = detail(fid2, t)
    check("登记草稿编辑空值显式置空",
          d["hx"].get("mgmtFeeAmount") is None and d["hx"].get("leaderEmail") is None,
          "mgmtFeeAmount=%r leaderEmail=%r" % (d["hx"].get("mgmtFeeAmount"), d["hx"].get("leaderEmail")))

    # 11b/Task28(N3 终审挂账):members=null(字段未传)=不动成员表;显式空列表=清空
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid2, members=None,
                                budgets=[{"subject": "业务费", "amount": "355000.00"}])).json()
    d = detail(fid2, t)
    check("编辑不传成员=成员表不动", r.get("code") == 0 and len(d["members"]) == 1,
          "members=%d" % len(d["members"]))
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid2, members=[],
                                budgets=[{"subject": "业务费", "amount": "355000.00"}])).json()
    d = detail(fid2, t)
    check("编辑显式空成员=清空", r.get("code") == 0 and len(d["members"]) == 0,
          "members=%d" % len(d["members"]))

    # 11c/Task28:uploadScan 扩展名白名单(pdf/png/jpg/jpeg 外拒)
    r = requests.post(BASE + "/hx/%d/scan" % fid2, headers=t, data={"docType": "登记表"},
                      files={"file": ("evil.exe", b"MZ fake")}).json()
    check("扫描件扩展名白名单拒", r.get("code") != 0 and "格式" in (r.get("msg") or ""),
          str(r.get("msg"))[:60])
    r = requests.post(BASE + "/hx/%d/scan" % fid2, headers=t, data={"docType": "登记表"},
                      files={"file": ("无扩展名", b"%PDF-1.4 fake")}).json()
    check("扫描件无扩展名拒", r.get("code") != 0, str(r.get("msg"))[:60])

    r = requests.delete(BASE + "/hx/%d" % fid2, headers=t)
    check("删草稿", r.json().get("code") == 0, str(r.json()))

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        raise SystemExit(1)

if __name__ == "__main__":
    main()
