# -*- coding: utf-8 -*-
"""
登记流程接口冒烟(N3 Task20):状态机教师侧全迁移+PDF双出+越权隔离。
前置:应用已启动(dev),模板四类启用版在库,LibreOffice 就位。
teacher/teacher123 为本人;teacher2/teacher2123 为 DataInitializer dev 种子(教师间越权用例)。
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

def main():
    t = login("teacher", "teacher123")
    admin = login("admin", "admin123")

    # 1. 草稿保存(项目+成员+预算一次提交)
    dto = {
        "name": "接口测试-智能化产线改造", "leaderName": "教师甲", "deptId": 1,
        "hx": {"projectType": "技术开发", "clientName": "测试委托公司", "clientNature": "企业",
                "contractAmount": "355000.00", "startDate": "2026-01-01", "endDate": "2027-12-31",
                "useSchoolAsset": 0, "chargeMgmtFee": 1, "mgmtFeeAmount": "10000.00",
                "leaderEmail": "t@a.cn", "projectSummary": "摘要", "progressPlan": "进度"},
        "members": [{"memberType": "教师", "name": "教师甲", "unit": "智能制造与装备学院",
                      "title": "教授", "major": "机械工程", "duty": "总体设计", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "80000.00"}],
    }
    r = requests.post(BASE + "/hx/register/draft", headers=t, json=dto).json()
    check("保存草稿", r.get("code") == 0 and r["data"] > 0, str(r.get("data")))
    fid = r["data"]

    # 2. 预览(分屏 docx 流:zip 魔数+无 {{ 残留)
    import io, zipfile
    dto2 = dict(dto); dto2["flowId"] = fid
    pv = requests.post(BASE + "/hx/preview", headers=t,
                       json={"flowType": "register", "data": dto2}, timeout=60)
    ok = pv.status_code == 200 and pv.content[:2] == b"PK"
    if ok:
        with zipfile.ZipFile(io.BytesIO(pv.content)) as z:
            ok = "{{" not in z.read("word/document.xml").decode("utf-8", "ignore")
    check("填报分屏预览 docx", ok, "%d bytes" % len(pv.content))

    # 3. 提交:status→1 + 双出待签文件
    r = requests.post(BASE + "/hx/register/submit", headers=t, json={"flowId": fid}).json()
    check("提交登记", r.get("code") == 0, str(r))
    d = requests.get(BASE + "/hx/%d" % fid, headers=t).json()["data"]
    check("状态=1已提交", d["flow"]["status"] == 1, str(d["flow"]["status"]))
    types = sorted(a["bizType"] for a in d["attachments"])   # 列表=生效件(作废不入列表)
    check("双出附件 hx_docx+hx_pdf", types == ["hx_docx", "hx_pdf"], str(types))

    # 4. 待签交付下载(docx 主交付魔数)
    dl = requests.get(BASE + "/hx/%d/deliver/docx" % fid, headers=t)
    check("待签docx下载", dl.status_code == 200 and dl.content[:2] == b"PK", "%d bytes" % len(dl.content))

    # 5. 撤回 1→0 → 再提交(幂等重渲染)
    requests.post(BASE + "/hx/%d/withdraw" % fid, headers=t)
    d = requests.get(BASE + "/hx/%d" % fid, headers=t).json()["data"]
    check("撤回回草稿", d["flow"]["status"] == 0, str(d["flow"]["status"]))
    r = requests.post(BASE + "/hx/register/submit", headers=t, json={"flowId": fid}).json()
    check("重新提交", r.get("code") == 0)

    # 6. 回扫上传(假 PDF 字节)→2;重传覆盖:生效件仍恰 1(旧件作废不入列表,设计§3.2)
    fake = b"%PDF-1.4 fake scan"
    r = requests.post(BASE + "/hx/%d/scan" % fid, headers=t, data={"docType": "登记表"},
                      files={"file": ("scan.pdf", fake)}).json()
    check("上传扫描件", r.get("code") == 0, str(r))
    r2 = requests.post(BASE + "/hx/%d/scan" % fid, headers=t, data={"docType": "登记表"},
                       files={"file": ("scan2.pdf", fake)}).json()
    d = requests.get(BASE + "/hx/%d" % fid, headers=t).json()["data"]
    scans = [a for a in d["attachments"] if a["bizType"] == "hx_scan"]
    check("重传覆盖旧件作废", r2.get("code") == 0 and d["flow"]["status"] == 2 and len(scans) == 1,
          "生效扫描件=%d" % len(scans))

    # 7. 时间线含 submit/withdraw/upload_scan 动作(撤回留痕=ACT_WITHDRAW)
    acts = [x["action"] for x in d["records"]]
    check("时间线动作", "submit" in acts and "upload_scan" in acts and "withdraw" in acts, str(acts))

    # 8. 越权与状态防御(teacher2 为 DataInitializer 新增种子)
    r = requests.get(BASE + "/hx/%d" % (fid + 99999), headers=t)
    check("不存在流程被拒", r.json().get("code") != 0, str(r.status_code))
    r = requests.get(BASE + "/hx/%d" % fid, headers=login("teacher2", "teacher2123"))
    check("教师间越权被拒", r.json().get("code") != 0, str(r.json().get("code")))
    r = requests.post(BASE + "/hx/%d/withdraw" % fid, headers=t)  # status=2 撤回→1 合法
    d = requests.get(BASE + "/hx/%d" % fid, headers=t).json()["data"]
    check("已回扫撤回到1", r.json().get("code") == 0 and d["flow"]["status"] == 1, str(d["flow"]["status"]))
    r = requests.post(BASE + "/hx/%d/scan" % fid, headers=admin, data={"docType": "登记表"},
                      files={"file": ("x.pdf", fake)})
    check("非本人操作被拒", r.json().get("code") != 0, str(r.json().get("code")))

    # 9. 我的列表含本项目
    my = requests.get(BASE + "/hx/my", headers=t).json()["data"]
    check("我的项目列表", any(x.get("flowId") == fid or x.get("projectId") for x in my), str(len(my)))

    # 10. 删草稿(新建草稿→删,连带成员预算附件逻辑删)
    r = requests.post(BASE + "/hx/register/draft", headers=t, json=dto).json()
    fid2 = r["data"]

    # 10a/终审补充(同 F1,MP NOT_NULL 策略):草稿带管理费金额保存→编辑清空可选字段重存→
    #     详情 mgmtFeeAmount/leaderEmail 须为空(旧 updateById 会残留 10000 渲进待签件)
    dto3 = dict(dto, flowId=fid2,
                hx=dict(dto["hx"], chargeMgmtFee=0, mgmtFeeAmount=None,
                        leaderEmail=None, projectSummary=None, progressPlan=None))
    r = requests.post(BASE + "/hx/register/draft", headers=t, json=dto3).json()
    assert r.get("code") == 0, "终审补充前置:编辑清空重存失败:" + str(r)
    d = requests.get(BASE + "/hx/%d" % fid2, headers=t).json()["data"]
    check("登记草稿编辑空值显式置空",
          d["hx"].get("mgmtFeeAmount") is None and d["hx"].get("leaderEmail") is None,
          "mgmtFeeAmount=%r leaderEmail=%r" % (d["hx"].get("mgmtFeeAmount"), d["hx"].get("leaderEmail")))

    # 10b/Task28(N3 终审挂账):members=null(字段未传)=不动成员表;显式空列表=清空
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid2, members=None)).json()
    d = requests.get(BASE + "/hx/%d" % fid2, headers=t).json()["data"]
    check("编辑不传成员=成员表不动", r.get("code") == 0 and len(d["members"]) == 1,
          "members=%d" % len(d["members"]))
    r = requests.post(BASE + "/hx/register/draft", headers=t,
                      json=dict(dto, flowId=fid2, members=[])).json()
    d = requests.get(BASE + "/hx/%d" % fid2, headers=t).json()["data"]
    check("编辑显式空成员=清空", r.get("code") == 0 and len(d["members"]) == 0,
          "members=%d" % len(d["members"]))

    # 10c/Task28:uploadScan 扩展名白名单(pdf/png/jpg/jpeg 外拒)
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
