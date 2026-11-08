# -*- coding: utf-8 -*-
"""生成《横向项目管理系统·使用手册(客户测试版)》单文件 HTML。
形式仿 docs/横向项目全流程单.html(gen_flow_doc.py):截图 base64 内嵌零外部依赖,
浏览器打开即看,Ctrl+P 另存 PDF;账号密码内附(客户测试环境,上线前统一更换)。
素材:docs/flow-shots/*.png(全流程演示跑批产物)。重跑本脚本即可重新生成。
输出:docs/横向项目使用手册.html
"""
import base64
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SHOTS = os.path.join(ROOT, "docs", "flow-shots")
OUT = os.path.join(ROOT, "docs", "横向项目使用手册.html")

SITE = "http://43.136.168.157"

TEACHERS = [
    ("210001", "王志远", "智能制造与装备学院", "教授", "123321"),
    ("210002", "李慧敏", "纺织与材料工程学院", "副教授", "123321"),
    ("210003", "张建华", "信息工程学院", "副教授", "123321"),
    ("210004", "刘晓燕", "经济管理学院", "讲师", "123321"),
    ("210005", "陈国强", "数字经济学院", "讲师", "123321"),
    ("210006", "杨丽娟", "建筑工程学院", "助教", "123321"),
    ("210007", "赵文博", "艺术设计学院", "教授", "123321"),
    ("210008", "周雪梅", "外语与文化旅游学院", "助教", "123321"),
]


def img64(name):
    with open(os.path.join(SHOTS, name), "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


# (截图, 角色标签, 步骤标题, 说明文字)
STEPS = [
    ("01-login.png", "教师端", "登录系统",
     f"浏览器打开 <b>{SITE}</b>,输入账号(工号)与密码登录。推荐 Chrome / Edge 最新版。"),
    ("02-myprojects-empty.png", "教师端", "我的项目 · 发起登记",
     "进入「横向项目」页面点击「新建登记」。列表实时汇总本人项目的执行状态、到账与在途流程;有新消息时右上角铃铛提示,点击直达对应流程。"),
    ("03-register-form-top.png", "教师端", "填报登记表 · 项目信息",
     "表格即填单页:项目名称、起止时间、委托单位(名称+性质勾选)、合同经费(自动换算大写)、负责人信息与联系电话自动带出,无需手填。"),
    ("03b-register-form-mid.png", "教师端", "填报登记表 · 成员与预算",
     "校内成员输入工号自动带出姓名/院部/职称(本手册八个测试工号可互相添加);预算六科目逐项填写,无比例限制的科目勾选「合同约定不受限」;预算合计须等于合同经费方可提交;收取管理费=是时金额按科技处设定的比例自动换算。"),
    ("04-flow-detail-precheck.png", "教师端", "提交 · 进入科技处预审",
     "提交后单据进入「预审中」。预审通过前教师仍可修改填报(科技处看到的始终是最新内容);灰色提示条会标出扫描件之后是否又改过数据。"),
    ("07-teacher-notify.png", "教师端", "接收预审通过通知",
     "科技处「预审通过」后教师收到通知,点击直达流程详情。预审通过后方可打印单据、上传盖章扫描件。"),
    ("08-print-preview.png", "教师端", "打印待签件 · 线下盖章",
     "打印页即为交付定稿:点「打印」或 Ctrl+P(A4、默认边距、勾选「背景图形」),可另存 PDF。单据自带学校信笺与每页水印;线下签字盖章后回扫。"),
    ("09-flow-detail-scanned.png", "教师端", "上传盖章扫描件",
     "将盖章扫描件(PDF/PNG/JPG,≤50MB)上传,单据进入「已回扫·审核中」;如需换章可「撤回」到待盖章态重新上传。"),
    ("13-myprojects-running.png", "教师端", "立项在研 · 我的台账",
     "科技处审核通过即入台账:自动分配项目编号(如 HX2026-001),我的项目列表实时反映最新状态;项目在研期间可发起变更、验收。"),
    ("05-admin-auditlist.png", "科技处端", "横向审批 · 待办列表",
     "科技处登录后在「横向审批」按状态筛选处理(教师草稿对科技处不可见)。列表显示单据类型、项目、负责人、院部与当前状态。"),
    ("06-admin-audit-detail.png", "科技处端", "审批详情 · 预审通过",
     "审批详情与教师端是同一张单据,可对照填报内容。「预审通过」后通知教师打印盖章;填报有误可「退回」并填写意见;也可「修正填报」代教师修改后再审。"),
    ("10-admin-audit-approve.png", "科技处端", "审核通过 · 入台账",
     "对照扫描件与单据审核,「通过」即入台账:自动分配项目编号、状态改「在研」并通知教师。"),
    ("11-admin-funding-add.png", "科技处端", "录入到账",
     "在「项目管理」展开项目行录入到账:金额、日期、付款方、银行流水号,录入即通知教师;累计到账实时求和(到账是验收的前置条件)。"),
    ("14-change-form.png", "教师端", "发起变更",
     "项目在研期间发起变更:勾选变更事项(经费/周期/负责人/委托内容/其他),系统自动带出对应项原信息;同一项目同时只能有一张在途变更单。"),
    ("17-acceptance-form.png", "教师端", "发起验收",
     "到账齐备后发起验收:登记成员自动带出并分配经费(比例+金额合计须等于到账);勾选成果形式、填写完成情况;验收报告按章节撰写,与验收单同时生成。"),
    ("18-acceptance-approve.png", "科技处端", "核定通过 · 结题",
     "验收单盖章回扫后,科技处录入「核定到账金额」并通过:项目状态改「结题」,核定额入台账,教师收到通知。"),
]

SECTIONS = [
    ("三、快速上手 · 登录与总览", 0, 2),
    ("四、教师端 · 项目登记全流程", 2, 9),
    ("五、科技处端 · 预审审核与到账", 9, 13),
    ("六、变更与验收(简述)", 13, 16),
]

FAQS = [
    ("用什么浏览器?", "推荐 Chrome 或 Edge 最新版(打印与每页水印兼容最佳),不支持 IE。"),
    ("待签件怎么打印?", "流程详情点「打印」进入打印页,再点「打印」或 Ctrl+P:纸张 A4、边距默认、缩放 100%,建议勾选「背景图形」;也可「另存为 PDF」留存。单据自带信笺与水印,打印版必须为最终修改版再拿去盖章。"),
    ("盖章扫描件有什么要求?", "PDF / PNG / JPG,单个不超过 50MB。上传后发现要换章:详情页「撤回」回到待盖章态,重新打印上传即可。"),
    ("密码忘了 / 被锁了怎么办?", "登录后右上角可自行修改密码;连续输错 5 次锁定 10 分钟,到点自动解锁;忘记密码请联系科技处管理员在「用户管理」中重置。"),
    ("预审通过前能改吗?", "能。预审通过前教师可随时修改填报,科技处看到的始终是最新内容;回扫审核中(2 态)仍可改,系统会提示单据晚于扫描件修改,科技处审批时按最新数据终检。"),
    ("数据安全吗?", "客户测试数据每周一凌晨自动全量备份;测试期密码为统一口径,正式上线前将统一更换并启用 HTTPS 加密访问。"),
]

CSS = """
:root{--blue:#14719F;--ink:#1f2d3d;--line:#d8e4ec;--bg:#f4f8fb}
*{box-sizing:border-box;margin:0;padding:0}
body{font:15px/1.75 "Microsoft YaHei","PingFang SC",sans-serif;color:var(--ink);background:var(--bg)}
.wrap{max-width:1000px;margin:0 auto;padding:32px 20px 60px}
header.hero{background:linear-gradient(135deg,#0f5c86,#1a86bd);color:#fff;border-radius:14px;padding:34px 36px;margin-bottom:26px}
header.hero h1{font-size:26px;letter-spacing:1px;margin-bottom:8px}
header.hero p{opacity:.92;font-size:14px}
.meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px 24px;margin-top:18px;font-size:13.5px}
.meta div{background:rgba(255,255,255,.12);border-radius:8px;padding:8px 14px}
.meta b{font-weight:600;margin-right:6px}
.meta .site{font-size:15px;letter-spacing:.5px}
.stat{display:flex;flex-wrap:wrap;gap:8px;align-items:center;background:#fff;border:1px solid var(--line);border-radius:12px;padding:16px 20px;margin-bottom:26px;font-size:13.5px}
.stat .node{background:#e8f4fb;color:var(--blue);border:1px solid #bcdff2;border-radius:6px;padding:2px 10px;font-weight:600;white-space:nowrap}
.stat .arr{color:#8aa6b8;font-weight:700}
.stat .note{color:#6b7f8d;margin-left:6px}
section{margin-bottom:34px}
section>h2{font-size:19px;color:var(--blue);border-left:5px solid var(--blue);padding-left:12px;margin-bottom:4px}
section>.sub{color:#6b7f8d;font-size:13px;margin:0 0 14px 17px}
.card{background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;margin-bottom:18px;box-shadow:0 1px 3px rgba(15,60,90,.05)}
.card .head{display:flex;align-items:center;gap:12px;padding:12px 18px;border-bottom:1px solid var(--line)}
.card .no{background:var(--blue);color:#fff;border-radius:50%;width:30px;height:30px;display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:700;flex:none}
.card .t{font-weight:700;font-size:15.5px;flex:1}
.card .role{font-size:12px;border-radius:20px;padding:2px 12px;white-space:nowrap}
.role.teacher{background:#e8f6ee;color:#1c7c4a;border:1px solid #bfe6d0}
.role.admin{background:#fdf1e3;color:#b26a00;border:1px solid #f3ddb8}
.card img{display:block;width:100%;border-top:1px solid var(--line)}
.card .desc{padding:12px 18px 14px;font-size:14px;color:#3b4a57;background:#fbfdfe;border-top:1px solid var(--line)}
.card.text .head{border-bottom:1px solid var(--line)}
.card.text .desc{border-top:none}
table.acc{width:100%;border-collapse:collapse;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;font-size:14px;margin-bottom:12px;box-shadow:0 1px 3px rgba(15,60,90,.05)}
table.acc th{background:#ddf2fc;color:#14719f;font-weight:600;padding:9px 12px;text-align:left;border:1px solid #c9e9f9;white-space:nowrap}
table.acc td{padding:8px 12px;border:1px solid var(--line)}
table.acc tr.adm td{background:#fffaf3}
.tip{background:#fff;border:1px solid #bcdff2;border-left:5px solid var(--blue);border-radius:10px;padding:12px 16px;font-size:13.5px;color:#33586e;margin:0 0 14px}
.warn{border-color:#f3ddb8;border-left-color:#e6a23c;color:#7a5518;background:#fffdf8}
footer{text-align:center;color:#8aa2b2;font-size:12.5px;margin-top:30px}
@media print{
  body{background:#fff} .wrap{max-width:none;padding:0}
  .card,table.acc{break-inside:avoid;box-shadow:none}
  header.hero{border-radius:0}
}
"""

ROLE_CLS = {"教师端": "teacher", "科技处端": "admin"}


def acc_rows():
    rows = ['<tr class="adm"><td><b>admin</b></td><td>科技处管理员(主)</td><td>科技处</td><td>审批 / 台账 / 到账 / 用户与系统参数</td><td><b>Kjc@2026</b></td></tr>',
            '<tr class="adm"><td><b>admin2</b></td><td>科技处管理员(备)</td><td>科技处</td><td>同上</td><td><b>123321</b></td></tr>']
    for u, n, d, t, p in TEACHERS:
        rows.append(f"<tr><td><b>{u}</b></td><td>{n}</td><td>{d}</td><td>{t} · 项目负责人(教师端)</td><td><b>{p}</b></td></tr>")
    return "\n".join(rows)


def build():
    no = 0
    sec_html = []
    for title, a, b in SECTIONS:
        buf = [f'<section><h2>{title}</h2>']
        for i in range(a, b):
            fn, role, head, desc = STEPS[i]
            no += 1
            buf.append(
                f'<div class="card"><div class="head"><div class="no">{no}</div>'
                f'<div class="t">{head}</div><span class="role {ROLE_CLS.get(role, "teacher")}">{role}</span></div>'
                f'<img src="{img64(fn)}" alt="{head}">'
                f'<div class="desc">{desc}</div></div>')
        buf.append("</section>")
        sec_html.append("\n".join(buf))

    faq = "\n".join(
        f'<div class="card text"><div class="head"><div class="no">?</div>'
        f'<div class="t">{q}</div></div><div class="desc">{a}</div></div>'
        for q, a in FAQS)

    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>横向项目管理系统 · 使用手册(客户测试版)</title>
<style>{CSS}</style>
</head>
<body>
<div class="wrap">
<header class="hero">
  <h1>横向项目管理系统 · 使用手册</h1>
  <p>科研管理平台 · 客户测试版 —— 填报 / 打印待签件 / 盖章回扫 / 预审审批 / 立项 · 变更 · 验收 · 到账</p>
  <div class="meta">
    <div><b>访问地址</b><span class="site">{SITE}</span></div>
    <div><b>浏览器</b>Chrome / Edge 最新版(推荐)</div>
    <div><b>账号口径</b>工号即登录名,初始密码见下表</div>
    <div><b>版本</b>客户测试版 · 2026-11</div>
  </div>
</header>
<div class="stat">
  <span class="node">填报提交</span><span class="arr">→</span>
  <span class="node">科技处预审</span><span class="arr">→</span>
  <span class="node">打印盖章</span><span class="arr">→</span>
  <span class="node">回扫上传</span><span class="arr">→</span>
  <span class="node">审核入台账</span>
  <span class="note">任一环节不符可退回修改重交;登记 / 变更 / 验收三种单据共用同一流程;系统不代签不代章,纸质意见留线下</span>
</div>

<section>
  <h2>一、系统简介</h2>
  <p class="sub">两类角色,同一平台</p>
  <div class="tip">横向项目全生命周期线上化管理:教师端<b>填报 → 打印待签件 → 线下盖章 → 回扫上传</b>;科技处端<b>预审 → 审核 → 立项入台账</b>,并管理到账、变更、验收与用户权限。打印件即交付定稿(HTML 直出,无需安装任何插件),每页自带信笺与水印。所有流程节点系统留痕、消息通知。</p>
</section>

<section>
  <h2>二、测试账号(内附初始密码)</h2>
  <p class="sub">教师工号可互相作为项目成员:登记 / 验收表填校内成员工号,自动带出姓名 / 院部 / 职称</p>
  <table class="acc">
    <tr><th>账号(工号)</th><th>姓名</th><th>所属单位</th><th>角色</th><th>初始密码</th></tr>
    {acc_rows()}
  </table>
  <div class="tip warn">测试期统一密码,仅用于本测试环境;连续输错 5 次锁定 10 分钟;请勿在环境内填写真实合同与个人信息。正式上线前将统一更换密码。</div>
</section>

{"".join(sec_html)}

<section>
  <h2>七、常见问题</h2>
  <p class="sub">客户测试高频问题速查</p>
  {faq}
</section>

<footer>科研管理平台 · 横向项目管理系统 客户测试版 · 2026-11</footer>
</div>
</body>
</html>"""
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(html)
    print(OUT, f"{os.path.getsize(OUT)/1024/1024:.2f} MB, {no} 步 + FAQ {len(FAQS)}")


if __name__ == "__main__":
    build()
