# -*- coding: utf-8 -*-
"""HTML 打印方案·结构自检(2026-10-13 建,2026-10-19 扩展)

校验 templates-src/html/ 三张审批单页 + 索引页:
  1. 三页 @media print 均以 .btoolbar, .hint, .scr 一并 display:none !important
  2. 所有 <details> 折叠块都挂 class="hint"(不漏打印隐藏)
  3. 每页提示数量达到口径(register>=4 / change>=4 / acceptance>=6)
  4. 工具条含「显示填写提示」总开关 + body.nohints 显隐机制
  5. 提示层样式块不含红色(踩坑#11 语义:提示色不进交付物)
  6. 索引页方案口径含打印「自动隐藏」条目
  7. 资产存在:assets/logo.png + assets/watermark.png
  8. 三页均有信笺 Logo(.hlogo)与居中镂空水印(.watermark),水印不进打印隐藏规则
  9. 三页蓝主题落地:旧灰底(#f5f5f5/#efefef/#fafafa)清零,新淡蓝底存在
 10. 登记表专属:7 列 colgroup、校内表头含工号、校外表头含职务(colspan2 姓名)、
     6 个「合同约定不受限」勾选框(.cbx)、管理费比例只读 2%(span#mrate,科技处设定口径,
     2026-10-20 由可编辑 input 改只读)
 11. 勾选组可点击(.opt):register 12 项(类型3+性质5+设备2+管理费2)、change 8(类别3+变更内容5multi)、
     acceptance 13(类型3+性质5+成果形式5multi);multi 组两页各有;性质组含政府部门选项
 12. 登记表「项目组成员及分工」竖排:td.vlbl + writing-mode:vertical-rl
 13. 表内字体全黑:三页 .grp/.origin-title 文字 color:#000(蓝只留在底色/悬停/屏显辅助);
     成员表列宽=姓名 8%/所在单位 18%(缩姓名、加长单位与任务)
 14. 水印可见且不糊字:笔画非白(平均亮度<200)+ 三页 opacity=0.30 + z-index:-1(置于内容之下)
 15. 工具条悬浮跟随浏览器窗口:fixed 视口右上 + .page 屏显顶部预留 42px 带(不压标题),打印隐藏
 16. 盖章签字区:.sign-block min-height:26mm + .sign-line 流内 flex 底对齐(height:26mm,
     不依赖 td min-height/不再绝对定位上溢——修复验收表科技处签字压核定人行)
 17. 变更表「变更内容」=勾选联动带出原信息:变更事项勾选组(5 选项带 data-key)+
     变更内容格 .cmpshow(JS 按勾选注入 ORIG 原信息行);不得残留 tr.cmp 对照表/origin 区
 18. 验收报告页(hx_report):表头 3 行+正文区(自由段落页数不限)+落款;勾选组 10 项
     (按期完成2单选+类型3单选+成果形式5multi);写作大纲折叠提示+手写落款脚注
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "templates-src" / "html"
PAGES = {"hx_register.html": 4, "hx_change.html": 4, "hx_acceptance.html": 6, "hx_report.html": 1}
PRINT_RULE = ".btoolbar, .hint, .scr { display:none !important; }"
OLD_GRAYS = ("#f5f5f5", "#efefef", "#fafafa")


def main():
    fails = []
    # 7) 资产存在
    for a in ("assets/logo.png", "assets/watermark.png"):
        if not (ROOT / a).exists():
            fails.append(f"资产缺失: {a}")

    for name, min_hints in PAGES.items():
        html = (ROOT / name).read_text(encoding="utf-8")
        # 1) 打印隐藏规则(含 .scr)
        if PRINT_RULE not in html:
            fails.append(f"{name}: @media print 未合并隐藏 .hint/.scr")
        # 2) details 全挂 hint
        for d in re.findall(r"<details\b[^>]*>", html):
            if 'class="hint"' not in d:
                fails.append(f"{name}: 存在未挂 hint 类的 <details>: {d}")
        # 3) 提示数量
        n_hints = len(re.findall(r'class="hint"', html))
        if n_hints < min_hints:
            fails.append(f"{name}: 提示数 {n_hints} < 口径 {min_hints}")
        # 4) 总开关
        if "显示填写提示" not in html or "nohints" not in html:
            fails.append(f"{name}: 缺总开关/显隐机制")
        # 5) 提示层无红
        m = re.search(r"/\* ===== 填写提示层.*?(?=/\* ===== 打印)", html, re.S)
        if not m:
            fails.append(f"{name}: 缺填写提示层样式块")
        elif re.search(r"red|#f00|#FF0000", m.group(0), re.I):
            fails.append(f"{name}: 提示层样式含红色")
        # 8) Logo + 水印(水印不得出现在隐藏规则里)
        if 'class="hlogo"' not in html:
            fails.append(f"{name}: 缺信笺 Logo(.hlogo)")
        if 'class="watermark"' not in html:
            fails.append(f"{name}: 缺居中水印(.watermark)")
        # 9) 蓝主题
        for g in OLD_GRAYS:
            if f"background:{g}" in html:
                fails.append(f"{name}: 残留旧灰底 {g}")
        if "background:#E9F6FD" not in html:
            fails.append(f"{name}: 缺新蓝主题字段标签底(#E9F6FD)")

    # 10) 登记表专属
    reg = (ROOT / "hx_register.html").read_text(encoding="utf-8")
    if len(re.findall(r"<col[>\s]", reg)) != 7:
        fails.append("hx_register: colgroup 非 7 列")
    if "<td>工号</td>" not in reg:
        fails.append("hx_register: 校内表头缺「工号」")
    if '<td colspan="2">姓名</td>' not in reg or "<td>职务</td>" not in reg:
        fails.append("hx_register: 校外表头缺「职务」/姓名跨列")
    n_cbx = len(re.findall(r'合同约定不受限</label>', reg))
    if n_cbx != 6:
        fails.append(f"hx_register: 「合同约定不受限」勾选框 {n_cbx} != 6")
    if '<input id="mrate"' in reg or '<span id="mrate">2</span>' not in reg:
        fails.append("hx_register: 管理费比例须为只读 2%(不得是可编辑 input)")
    if "由科技处在系统参数中统一设定" not in reg or "教师不可修改" not in reg:
        fails.append("hx_register: 管理费比例提示文案缺失(科技处设定口径)")
    # 12) 竖排标签
    if 'class="lbl vlbl" rowspan="7">项目组成员及分工' not in reg:
        fails.append("hx_register: 「项目组成员及分工」未挂 vlbl 竖排")
    if "writing-mode:vertical-rl" not in reg:
        fails.append("hx_register: 缺竖排 CSS(writing-mode)")

    # 11) 勾选组可点击
    OPTS = {"hx_register.html": 12, "hx_change.html": 8, "hx_acceptance.html": 13, "hx_report.html": 10}
    for name, need in OPTS.items():
        h = (ROOT / name).read_text(encoding="utf-8")
        n_opt = len(re.findall(r'class="opt"', h))
        if n_opt != need:
            fails.append(f"{name}: 可点击勾选选项 {n_opt} != {need}")
        if ".opt:hover" not in h:
            fails.append(f"{name}: 缺 .opt 屏显样式")
        if name != "hx_register.html" and 'class="chk multi"' not in h:
            fails.append(f"{name}: 缺 multi 多选组")
    if '<span class="opt">□政府部门</span>' not in reg:
        fails.append("hx_register: 性质组不可勾选(缺政府部门 opt)")

    # 13) 表内字体全黑 + 成员表列宽
    grp_black = ".grp td { background:#DDF2FC; text-align:center; letter-spacing:6px; color:#000; }"
    if grp_black not in reg:
        fails.append("hx_register: 组头字体未黑")
    acc = (ROOT / "hx_acceptance.html").read_text(encoding="utf-8")
    if grp_black not in acc:
        fails.append("hx_acceptance: 组头字体未黑")
    chg = (ROOT / "hx_change.html").read_text(encoding="utf-8")
    # 17) 变更内容=勾选联动带出原信息
    if len(re.findall(r'<span class="opt" data-key="', chg)) != 5 or "ORIG" not in chg or "syncCmp" not in chg:
        fails.append("hx_change: 勾选组未带 data-key/缺 ORIG·syncCmp 联动")
    if 'class="cmpshow"' not in chg or '<td class="lbl">变更事项</td>' not in chg or '<td class="lbl">变更内容</td>' not in chg:
        fails.append("hx_change: 变更事项/变更内容结构缺失")
    if "tr.cmp" in chg or "origin-title" in chg or "变更内容说明" in chg:
        fails.append("hx_change: 残留对照表/原信息区/旧字段名")
    if '<col style="width:8%"><col style="width:18%">' not in reg:
        fails.append("hx_register: 成员表列宽未调(姓名8%/所在单位18%)")
    if 'id="btotal"' not in reg or "calcBudget" not in reg:
        fails.append("hx_register: 预算合计未实时计算(id=btotal/calcBudget)")

    # 14) 水印可见性:笔画非白 + opacity 0.30
    try:
        from PIL import Image
        wim = Image.open(ROOT / "assets/watermark.png").convert("RGBA")
        wpx = wim.load()
        tw, th = wim.size
        s = n = 0
        for y in range(0, th, 4):
            for x in range(0, tw, 4):
                r, g, b, a = wpx[x, y]
                if a == 0:
                    continue
                s += (r * 299 + g * 587 + b * 114) // 1000
                n += 1
        mean_lum = s / n if n else 255
        if mean_lum >= 200:
            fails.append(f"watermark.png: 笔画偏白(平均亮度 {mean_lum:.0f} ≥ 200),白底不可见")
    except ImportError:
        fails.append("自检需要 PIL(校验水印颜色)")
    for name in PAGES:
        h = (ROOT / name).read_text(encoding="utf-8")
        if "z-index:-1; opacity:.30; }" not in h or "opacity:.30; }" not in h.split("@media print")[-1]:
            fails.append(f"{name}: 水印未置于内容下(z-index:-1)或 opacity 未调至 0.30")
        if "padding:52px 6px 0;" not in h:
            fails.append(f"{name}: 页面顶部未预留悬浮工具条 42px 带")
        i_bar, i_top = h.find('class="btoolbar"'), h.find('class="toparea"')
        if not (0 <= i_bar < i_top):
            fails.append(f"{name}: 工具条文档序应先于标题区")
        if ".btoolbar { position:fixed" not in h:
            fails.append(f"{name}: 工具条未悬浮(fixed)")
        if ".sign-block { min-height:26mm;" not in h:
            fails.append(f"{name}: 盖章签字区未加大(26mm)")
        if "display:flex; flex-direction:column; justify-content:flex-end; align-items:flex-end; height:26mm;" not in h:
            fails.append(f"{name}: 签字行未改流内 flex 底对齐(防 td 塌陷上溢)")

    idx = (ROOT / "index.html").read_text(encoding="utf-8")
    if "自动隐藏" not in idx:
        fails.append("index.html: 方案口径缺「自动隐藏」条目")
    if fails:
        print("FAIL")
        for f in fails:
            print(" -", f)
        sys.exit(1)
    print("PASS: 4 页结构自检全部通过")


if __name__ == "__main__":
    main()
