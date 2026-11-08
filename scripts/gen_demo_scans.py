# -*- coding: utf-8 -*-
"""生成演示用「盖章扫描件」PDF(单页 A4,红色公章示意)。
用途:全流程演示跑批时模拟教师线下盖章后回扫上传的扫描件素材。
输出:scripts/cache/scan-{register,change,acceptance}.pdf
"""
import os

import fitz  # PyMuPDF

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "cache")

TITLES = {
    "register": "成都纺织高等专科学校横向项目登记表",
    "change": "成都纺织高等专科学校横向项目变更审批表",
    "acceptance": "成都纺织高等专科学校横向项目验收审批表",
}
COMPANY = "江苏瑞泰安全防护科技有限公司"


def draw_stamp(page, cx, cy, r):
    """红色双圈公章示意 + 单位名 + 五角星。"""
    red = (0.82, 0.12, 0.12)
    page.draw_circle((cx, cy), r, color=red, width=2.2)
    page.draw_circle((cx, cy), r * 0.82, color=red, width=1.1)
    # 五角星
    import math
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = r * 0.42 if i % 2 == 0 else r * 0.18
        pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang) + r * 0.05))
    page.draw_polyline(pts + [pts[0]], color=red, width=1.2, fill=red, closePath=True)
    # 圈下方单位名(弧排简化为横排两行)
    page.insert_textbox(
        fitz.Rect(cx - r * 1.35, cy + r * 0.55, cx + r * 1.35, cy + r * 1.15),
        COMPANY, fontsize=10, color=red, align=fitz.TEXT_ALIGN_CENTER, fontname="china-s")
    page.insert_textbox(
        fitz.Rect(cx - r * 1.35, cy + r * 1.05, cx + r * 1.35, cy + r * 1.6),
        "合同专用章", fontsize=9, color=red, align=fitz.TEXT_ALIGN_CENTER, fontname="china-s")


def make(path_key):
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)  # A4 竖版
    # 标题与表单示意行(浅灰,模拟扫描底)
    page.insert_textbox(fitz.Rect(60, 70, 535, 110), TITLES[path_key],
                        fontsize=16, align=fitz.TEXT_ALIGN_CENTER, fontname="china-s")
    gray = (0.55, 0.55, 0.55)
    y = 150
    for i in range(18):
        page.draw_line(fitz.Point(70, y), fitz.Point(525, y), color=(0.8, 0.8, 0.8), width=0.6)
        if i % 3 == 0:
            page.insert_text(fitz.Point(72, y - 4), f"栏目 {i + 1}:__________________________",
                             fontsize=9, color=gray, fontname="china-s")
        y += 26
    # 公章:页面中部偏右
    draw_stamp(page, 400, 520, 62)
    # 骑缝/落款日期
    page.insert_text(fitz.Point(340, 640), "2026 年 11 月", fontsize=10, color=(0.2, 0.2, 0.2), fontname="china-s")
    out = os.path.join(OUT_DIR, f"scan-{path_key}.pdf")
    doc.save(out)
    doc.close()
    return out


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    for key in TITLES:
        print(make(key))
