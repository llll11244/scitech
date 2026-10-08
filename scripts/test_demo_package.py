# -*- coding: utf-8 -*-
"""
演示包一键验收(Task29):对已 start.bat 启动的演示包(默认 http://localhost:8080)验证——
① 前端 SPA 由后端直出:首页/静态资源/深链回退(SPA 路由刷新不 404);
② /api 不被静态上下文劫持:POST /api/auth/login 正常出 token;
③ 演示数据在库:项目A(结题)/项目B(在研)出现在台账;
④ 渲染管线全通:teacher 当场登记新项目提交→待签 docx/PDF 出件且可下载(docx=zip 头、
   pdf=%PDF 头)——覆盖 poi-tl 渲染+LibreOffice 转换在演示包内的真实链路。
用法:python scripts/test_demo_package.py [--base http://localhost:8080]
"""
import argparse
import re
import sys

import requests

results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login(base, u, p):
    r = requests.post(base + "/auth/login", json={"username": u, "password": p}, timeout=15).json()
    assert r.get("code") == 0, "登录失败 %s: %s" % (u, r.get("msg"))
    return {"Authorization": "Bearer " + r["data"]["token"]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    args = ap.parse_args()
    base = args.base.rstrip("/")          # 根(前端与 /api 同源直出)
    api = base + "/api"

    # ---------------- ① SPA 静态直出 ----------------
    r = requests.get(base + "/", timeout=15)
    check("首页 / 返回 index.html", r.status_code == 200 and 'id="app"' in r.text,
          "%d %s" % (r.status_code, r.headers.get("Content-Type", "")))
    js = re.search(r'src="(/assets/[^"]+\.js)"', r.text)
    check("index 引用 assets 资源", js is not None, js.group(1) if js else "未找到")
    if js:
        a = requests.get(base + js.group(1), timeout=15)
        check("静态 JS 可达且 MIME 正确",
              a.status_code == 200 and len(a.content) > 1000
              and a.headers.get("Content-Type", "").startswith("text/javascript"),
              "%d %s %d bytes" % (a.status_code, a.headers.get("Content-Type", ""), len(a.content)))
    deep = requests.get(base + "/login", timeout=15)
    check("深链 /login 刷新回退 index(SPA 不 404)",
          deep.status_code == 200 and 'id="app"' in deep.text, str(deep.status_code))
    stray = requests.get(base + "/not/a/route/at/all", timeout=15)
    check("任意未知路径同样回退 index",
          stray.status_code == 200 and 'id="app"' in stray.text, str(stray.status_code))

    # ---------------- ② /api 不被劫持 ----------------
    admin = login(api, "admin", "admin123")
    teacher = login(api, "teacher", "teacher123")
    check("POST /api/auth/login 正常(admin/teacher)", True)

    # ---------------- ③ 演示数据在库 ----------------
    rows = requests.get(api + "/hx/projects", headers=admin, timeout=30).json().get("data") or []
    names = {x.get("name") for x in rows}
    check("演示项目A(结题)在台账", "高性能纺织智能检测装备研发" in names
          and any(x.get("name") == "高性能纺织智能检测装备研发" and x.get("execStatus") == "已结题"
                  for x in rows), "")
    brow = next((x for x in rows if x.get("name") == "功能性针织面料数字化配色系统开发"), None)
    check("演示项目B(在研+到账2笔)在台账", brow is not None and brow.get("execStatus") == "在研"
          and str(brow.get("receivedTotal")) in ("224000", "224000.0"),
          str(brow.get("receivedTotal")) if brow else "缺行")

    # ---------------- ④ 当场登记→渲染→下载(渲染管线全通) ----------------
    reg = {
        "name": "演示验收-一键包冒烟项目", "leaderName": "测试教师", "deptId": 9,
        "hx": {"projectType": "技术服务", "clientName": "演示包冒烟委托单位", "clientNature": "企业",
               "contractAmount": "100000.00", "startDate": "2026-10-01", "endDate": "2027-09-30",
               "useSchoolAsset": 0, "chargeMgmtFee": 1, "mgmtFeeAmount": "3000.00",
               "leaderEmail": "teacher@example.edu.cn",
               "projectSummary": "演示包验收冒烟项目:一键起后当场登记提交,验证渲染管线。",
               "progressPlan": "start.bat 一键起;登记提交出待签件;下载 docx 与 PDF 人工核验。"},
        "members": [{"memberType": "教师", "name": "测试教师", "unit": "纺织与材料工程学院",
                     "title": "副教授", "major": "纺织材料", "duty": "负责", "sortNo": 1}],
        "budgets": [{"subject": "业务费", "amount": "30000.00"}],
    }
    fid = requests.post(api + "/hx/register/draft", headers=teacher, json=reg, timeout=120).json()["data"]
    r = requests.post(api + "/hx/register/submit", headers=teacher, json={"flowId": fid}, timeout=180).json()
    check("登记新项目提交成功", r.get("code") == 0, "flowId=%s %s" % (fid, r.get("msg")))
    d = requests.get(api + "/hx/%d" % fid, headers=teacher, timeout=30).json()["data"]
    atts = {a["bizType"]: a for a in d["attachments"] if ("/%d/" % fid) in (a.get("filePath") or "")}
    check("待签 docx+pdf 双出落库", "hx_docx" in atts and "hx_pdf" in atts,
          str(sorted(atts)))
    # 待签件走专用交付端点(通用附件下载端点不开放 hx_docx/hx_pdf 直下)
    docx = requests.get(api + "/hx/%d/deliver/docx" % fid, headers=teacher, timeout=120)
    check("待签 docx 可下载(zip 头)",
          docx.status_code == 200 and docx.content[:2] == b"PK" and len(docx.content) > 10000,
          "%d bytes" % len(docx.content))
    pdf = requests.get(api + "/hx/%d/deliver/pdf" % fid, headers=teacher, timeout=120)
    check("待签 pdf 可下载(%PDF 头,LibreOffice 真转换)",
          pdf.status_code == 200 and pdf.content[:4] == b"%PDF" and len(pdf.content) > 1000,
          "%d bytes" % len(pdf.content))

    # 项目A 终版四件下载抽验(演示数据完整可看;my 按项目分组,flows 子列表取 flowId)
    projects = requests.get(api + "/hx/my", headers=teacher, timeout=30).json().get("data") or []
    ok_final = False
    detail_list = []
    for row in projects:
        for f in row.get("flows") or []:
            if f.get("flowType") != "acceptance":
                continue
            dd = requests.get(api + "/hx/%d" % f["flowId"], headers=teacher, timeout=30).json()["data"]
            finals = [a for a in dd["attachments"]
                      if a["bizType"].startswith("hx_final")
                      and ("/acceptance/%d/" % f["flowId"]) in (a.get("filePath") or "")]
            if len(finals) >= 4:
                sample = requests.get(api + "/hx/%d/deliver-final/docx" % f["flowId"],
                                      headers=teacher, timeout=120)
                ok_final = sample.status_code == 200 and sample.content[:2] == b"PK"
                detail_list = [a["bizType"] for a in finals]
                break
        if detail_list:
            break
    check("项目A 终版四件在库可下载", ok_final, str(sorted(detail_list)))

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
