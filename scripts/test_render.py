# -*- coding: utf-8 -*-
"""
渲染管线接口冒烟(Task 12 + 2026-10-07 直出 Word 扩展):
  前置 scripts/test_template.py 已上传 hx_register v1
  1. 上传四类模板——确保启用版齐备(幂等)
  2. 四类 /template/preview 样例 PDF 预览:200 + %PDF 魔数 + 大小>10KB
  3. 四类 /template/preview-docx 样例 docx 直出:zip 魔数 + 占位符全填充
  4. /config/deliver_pdf_visible 开关读数(默认 false=隐藏 PDF 入口)
前置:应用(dev)已启动,LibreOffice 就位
"""
import os
import sys

import requests

BASE = "http://localhost:8080/api"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TPL_DIR = os.path.join(ROOT, "templates-src")

TYPES = ["hx_register", "hx_change", "hx_acceptance", "hx_report"]
results = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS" if ok else "FAIL"), "-", name, detail)


def login():
    r = requests.post(BASE + "/auth/login", json={"username": "admin", "password": "admin123"}, timeout=10).json()
    return {"Authorization": "Bearer " + r["data"]["token"]}


def main():
    headers = login()

    # 1. 四类模板上传齐备(已启用的会滚动到新版本,幂等无害)
    for t in TYPES:
        with open(os.path.join(TPL_DIR, t + ".docx"), "rb") as f:
            r = requests.post(BASE + "/template/upload", headers=headers,
                              data={"tplType": t}, files={"file": (t + ".docx", f)}, timeout=180).json()
        check("上传启用 " + t, r.get("code") == 0 and r["data"]["status"] == 1)

    # 2. 四类样例预览
    for t in TYPES:
        resp = requests.get(BASE + "/template/preview", headers=headers,
                            params={"tplType": t}, timeout=120)
        ok = resp.status_code == 200 and resp.content[:4] == b"%PDF" and len(resp.content) > 10_000
        check("预览 " + t, ok, "%d bytes" % len(resp.content))

    # 3. 四类样例 docx 直出预览(2026-10-07 直出 Word 决策):zip 魔数 + 占位符已全填充
    import io as _io
    import zipfile as _zip
    for t in TYPES:
        resp = requests.get(BASE + "/template/preview-docx", headers=headers,
                            params={"tplType": t}, timeout=60)
        ok = resp.status_code == 200 and resp.content[:2] == b"PK"
        if ok:
            with _zip.ZipFile(_io.BytesIO(resp.content)) as z:
                xml = z.read("word/document.xml").decode("utf-8", "ignore")
                ok = "{{" not in xml
                if t == "hx_report":
                    # 正文存在性断言(终审 F3):{{this}} List 缺陷的失败模式=渲空,
                    # 「无 {{ 残留」会放行;模板本身无该串(净版),出现即证样例正文真渲染
                    ok = ok and "项目目标达成情况" in xml
        check("docx预览 " + t, ok, "%d bytes" % len(resp.content))

    # 4. 交付开关读数(sys_config.deliver_pdf_visible,默认 false=隐藏 PDF 入口)
    resp = requests.get(BASE + "/config/deliver_pdf_visible", headers=headers, timeout=10).json()
    check("交付开关", resp.get("code") == 0 and resp["data"] == "false", str(resp.get("data")))

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
