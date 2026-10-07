# -*- coding: utf-8 -*-
"""
渲染管线接口冒烟(计划 Task 12):
  前置 scripts/test_template.py 已上传 hx_register v1
  1. 上传其余三类模板(change/acceptance/report)——确保四类启用版齐备
  2. 四类 /template/preview 样例预览:200 + %PDF 魔数 + 大小>10KB
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

    failed = [n for n, ok in results if not ok]
    print("\n%d/%d 通过" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
