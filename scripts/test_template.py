# -*- coding: utf-8 -*-
"""
模板管理接口测试(计划 Task 11):5 断言
  1. 上传合法 hx_register 模板 → v1 启用
  2. 缺占位符(把 projectName 换成错误 key) → 明确报缺失名单
  3. 非 docx 拒
  4. 页数超限拒(合法模板+200 空段→3页)
  5. 版本历史列表 + 下载 docx
前置:应用(dev)已启动,LibreOffice 就位,templates-src/hx_register.docx 存在
"""
import io
import os
import sys
import zipfile

import requests

BASE = "http://localhost:8080/api"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "templates-src", "hx_register.docx")

results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login():
    r = requests.post(BASE + "/auth/login", json={"username": "admin", "password": "admin123"}, timeout=10).json()
    return {"Authorization": "Bearer " + r["data"]["token"]}


def rewrites_docx(path, replacements, extra_paras=0):
    """读 docx,替换 document.xml 文本,可追加空段;返回内存字节"""
    with zipfile.ZipFile(path) as z:
        items = {i: z.read(i) for i in z.namelist()}
    xml = items["word/document.xml"].decode("utf-8")
    for old, new in replacements:
        assert old in xml, "构造失败:找不到 " + old
        xml = xml.replace(old, new)
    if extra_paras:
        xml = xml.replace("</w:body>", "<w:p/>" * extra_paras + "</w:body>")
    items["word/document.xml"] = xml.encode("utf-8")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in items.items():
            z.writestr(name, data)
    return buf.getvalue()


def main():
    headers = login()
    with open(SRC, "rb") as f:
        tpl_bytes = f.read()

    # 1. 合法上传
    r = requests.post(BASE + "/template/upload", headers=headers,
                      data={"tplType": "hx_register"},
                      files={"file": ("hx_register.docx", tpl_bytes)}, timeout=180).json()
    check("合法模板上传v1启用", r.get("code") == 0 and r["data"]["version"] == 1 and r["data"]["status"] == 1, str(r)[:120])

    # 2. 缺占位符(projectName→typo)
    bad = rewrites_docx(SRC, [("{{projectName}}", "{{projectNam}")])
    r = requests.post(BASE + "/template/upload", headers=headers,
                      data={"tplType": "hx_register"},
                      files={"file": ("bad.docx", bad)}, timeout=60).json()
    check("缺占位符报缺名", r.get("code") != 0 and "projectName" in r.get("msg", ""), r.get("msg", "")[:100])

    # 3. 非 docx
    r = requests.post(BASE + "/template/upload", headers=headers,
                      data={"tplType": "hx_register"},
                      files={"file": ("x.txt", b"not a docx")}, timeout=30).json()
    check("非docx拒绝", r.get("code") != 0 and ".docx" in r.get("msg", ""), r.get("msg", "")[:60])

    # 4. 页数超限(占位符齐全+200空段→3页)
    heavy = rewrites_docx(SRC, [], extra_paras=200)
    r = requests.post(BASE + "/template/upload", headers=headers,
                      data={"tplType": "hx_register"},
                      files={"file": ("heavy.docx", heavy)}, timeout=180).json()
    check("页数超限拒绝", r.get("code") != 0 and "页" in r.get("msg", ""), r.get("msg", "")[:80])

    # 5. 列表+下载
    r = requests.get(BASE + "/template/list", headers=headers, params={"tplType": "hx_register"}, timeout=10).json()
    ok_list = r.get("code") == 0 and len(r.get("data") or []) >= 1
    dl_ok = False
    if ok_list:
        tid = r["data"][0]["id"]
        resp = requests.get(BASE + "/template/%d/download" % tid, headers=headers, timeout=30)
        dl_ok = resp.status_code == 200 and resp.content[:2] == b"PK"
    check("版本列表+下载docx", ok_list and dl_ok)

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
