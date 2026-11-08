# -*- coding: utf-8 -*-
"""生成《横向项目全流程单》单文件 HTML(截图 base64 内嵌,零外部依赖)。
素材:docs/flow-shots/*.png(全流程演示跑批产物,scripts 会话 2026-10-28)
输出:docs/横向项目全流程单.html —— 浏览器打开即看,Ctrl+P 另存 PDF 归档。
重复生成:重跑本脚本即可(截图更新后重出)。
"""
import base64
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SHOTS = os.path.join(ROOT, "docs", "flow-shots")
OUT = os.path.join(ROOT, "docs", "横向项目全流程单.html")


def img64(name):
    with open(os.path.join(SHOTS, name), "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


# (截图文件, 角色标签, 步骤标题, 说明文字)
STEPS = [
    # ---- 阶段一 登记 ----
    ("01-login.png", "教师端", "登录系统",
     "项目负责人使用学校分配的账号登录科研管理平台。教师端提供项目登记、变更、验收的全程填报入口;科技处端负责预审、审批与台账管理。"),
    ("02-myprojects-empty.png", "教师端", "我的项目 · 发起登记",
     "进入「横向项目」页面,点击「新建登记」开始填报。列表实时汇总本人项目的执行状态、到账与在途流程。"),
    ("03-register-form-top.png", "教师端", "填报登记表 · 项目信息",
     "表格即填单页:项目名称、起止时间、委托单位(名称+性质勾选)、合同经费(自动换算大写)、负责人与院部、联系电话自动带出。"),
    ("03b-register-form-mid.png", "教师端", "填报登记表 · 成员与预算",
     "校内成员输入工号自动带出姓名/院部/职称,校外成员手工填报;预算六科目逐项填写,无比例限制的科目勾选「合同约定不受限」;预算合计实时计算,须等于合同经费方可提交;收取管理费=是时金额按科技处设定的比例(本例 2%)自动换算,教师不可修改。"),
    ("04-flow-detail-precheck.png", "教师端", "提交 · 进入科技处预审",
     "提交后单据进入「预审中」(状态 0 草稿 → 1 预审中)。流程详情页可查看只读单据、状态时间线;预审通过前教师仍可修改,科技处预审看到的始终是最新内容。"),
    ("05-admin-auditlist.png", "科技处端", "横向审批 · 待预审列表",
     "科技处在「横向审批」列表按状态筛选处理(草稿对科技处不可见)。列表显示单据类型、项目、负责人、院部与当前状态。"),
    ("06-admin-audit-detail.png", "科技处端", "审批详情 · 预审通过",
     "审批详情与教师端看到的是同一张单据,可对照填报内容。点击「预审通过」(1 → 5 待盖章),系统自动通知教师;如填报有误也可「退回」并填写意见。"),
    ("07-teacher-notify.png", "教师端", "接收通知",
     "教师收到「预审通过」通知,点击直达流程详情。预审通过后方可打印单据、上传盖章扫描件——1 态直接上传会被系统拒绝。"),
    ("08-print-preview.png", "教师端", "打印待签件(HTML 即交付)",
     "浏览器打印页即为交付定稿:Ctrl+P 打印 A4 或另存 PDF。单据带学校信笺、水印,每页重复;线下签字盖章后回扫。"),
    ("09-flow-detail-scanned.png", "教师端", "上传盖章扫描件",
     "将盖章后的扫描件(PDF/图片)上传系统,单据进入「已回扫·审核中」(5 → 2)。扫描件与最新填报数据的差异系统会自动提示;如需换章可「撤回」到待盖章态。"),
    ("10-admin-audit-approve.png", "科技处端", "审核通过 · 入台账",
     "科技处对照扫描件与单据审核,「通过」即入台账:自动分配项目编号(本例 HX2026-001)、状态改「在研」、登记确认日写入,并通知教师。"),
    # ---- 阶段二 到账 ----
    ("11-admin-funding-add.png", "科技处端", "录入到账",
     "科技处在「项目管理」展开项目行录入到账:金额、到账日期、付款方、银行流水号。录入即通知教师;累计到账实时求和。"),
    ("12-admin-funding-list.png", "科技处端", "到账明细 · 实时累计",
     "本例两期到账 200,000 + 220,000 = 420,000 元。到账是验收的前置:无到账不能提交验收,使用经费按到账经费核算。"),
    # ---- 阶段三 变更 ----
    ("14-change-form.png", "教师端", "发起变更",
     "项目在研期间发起变更:勾选变更事项(经费/周期/负责人/委托内容/其他),系统自动带出对应项原信息;填写变更后内容与原因后提交,进入预审。同一项目同时只能有一张在途变更单。"),
    ("15-change-audit.png", "科技处端", "变更审批",
     "变更单与登记单走同一状态机:预审 → 盖章回扫 → 审核。审批页可对照变更前后信息;「通过」即按变更内容改写项目字段并通知教师。"),
    ("16-change-effective.png", "教师端", "变更生效",
     "本例项目先后完成「执行周期顺延至 2027-12-31」「合同经费 350,000 → 420,000」两次变更:经费改写后管理费按当前比例自动重算(8,400 元),我的项目列表实时反映最新数据。"),
    # ---- 阶段四 验收 ----
    ("17-acceptance-form.png", "教师端", "发起验收",
     "到账齐备后发起验收:登记成员自动带出并分配经费(比例+金额,合计须等于到账);到账经费 42 万元系统只读带出;勾选成果形式、填写完成情况;验收报告按章节撰写,与验收单同时生成。"),
    ("18-acceptance-approve.png", "科技处端", "核定通过 · 结题",
     "验收单盖章回扫后,科技处录入「核定到账金额」并通过:项目状态改「结题」、核定额入台账,教师收到验收通过通知。"),
    # ---- 阶段五 终态 ----
    ("19-admin-final.png", "科技处端", "台账终态",
     "项目管理列表呈现项目全生命周期数据:编号、经费、到账累计、执行状态(结题);行展开即到账明细。"),
    ("20-teacher-notify-final.png", "教师端", "全程通知留痕",
     "预审通过、审批通过、到账、变更生效、验收结题——每个关键节点均有消息通知,点击直达对应流程。"),
    ("21-teacher-final.png", "教师端", "我的项目 · 已结题",
     "项目全流程闭环:登记 → 预审 → 盖章回扫 → 立项 → 到账 → 变更 → 验收 → 结题。"),
]

SECTIONS = [
    ("一、登记(立项)", 0, 11, "0 草稿 → 1 预审中 → 5 待盖章 → 2 审核中 → 3 通过入台账"),
    ("二、到账管理", 11, 13, "科技处录入,实时累计,验收前置"),
    ("三、项目变更", 13, 16, "与登记同一状态机;通过即改写项目字段"),
    ("四、验收(结题)", 16, 18, "按到账核算成员分配;核定通过即结题"),
    ("五、终态与留痕", 18, 21, "台账、通知、列表三视角闭环"),
]

CSS = """
:root{--blue:#14719F;--ink:#1f2d3d;--line:#d8e4ec;--bg:#f4f8fb;--tag-t:#fff;--tag-a:#e8f4fb}
*{box-sizing:border-box;margin:0;padding:0}
body{font:15px/1.75 "Microsoft YaHei","PingFang SC",sans-serif;color:var(--ink);background:var(--bg)}
.wrap{max-width:1000px;margin:0 auto;padding:32px 20px 60px}
header.hero{background:linear-gradient(135deg,#0f5c86,#1a86bd);color:#fff;border-radius:14px;padding:34px 36px;margin-bottom:26px}
header.hero h1{font-size:26px;letter-spacing:1px;margin-bottom:8px}
header.hero p{opacity:.92;font-size:14px}
.meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px 24px;margin-top:18px;font-size:13.5px}
.meta div{background:rgba(255,255,255,.12);border-radius:8px;padding:8px 14px}
.meta b{font-weight:600;margin-right:6px}
.stat{display:flex;flex-wrap:wrap;gap:8px;align-items:center;background:#fff;border:1px solid var(--line);border-radius:12px;padding:16px 20px;margin-bottom:26px;font-size:13.5px}
.stat .node{background:var(--tag-a);color:var(--blue);border:1px solid #bcdff2;border-radius:6px;padding:2px 10px;font-weight:600;white-space:nowrap}
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
.role.line{background:#f0eefd;color:#5561d6;border:1px solid #d9dcf7}
.card img{display:block;width:100%;border-top:1px solid var(--line)}
.card .desc{padding:12px 18px 14px;font-size:14px;color:#3b4a57;background:#fbfdfe;border-top:1px solid var(--line)}
footer{text-align:center;color:#8aa2b2;font-size:12.5px;margin-top:30px}
@media print{
  body{background:#fff} .wrap{max-width:none;padding:0}
  .card{break-inside:avoid;box-shadow:none}
  header.hero{border-radius:0}
  section{break-inside:auto}
}
"""

ROLE_CLS = {"教师端": "teacher", "科技处端": "admin", "线下": "line"}


def build():
    cards = []
    no = 0
    sec_html = []
    for title, a, b, sub in SECTIONS:
        buf = [f'<section><h2>{title}</h2><p class="sub">状态线:{sub}</p>']
        for i in range(a, b):
            fn, role, head, desc = STEPS[i]
            no += 1
            buf.append(
                f'<div class="card"><div class="head"><div class="no">{no}</div>'
                f'<div class="t">{head}</div><span class="role {ROLE_CLS.get(role, "line")}">{role}</span></div>'
                f'<img src="{img64(fn)}" alt="{head}">'
                f'<div class="desc">{desc}</div></div>')
        buf.append("</section>")
        sec_html.append("\n".join(buf))

    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>横向项目全流程单 · 科研管理平台</title>
<style>{CSS}</style>
</head>
<body>
<div class="wrap">
<header class="hero">
  <h1>横向项目全流程单</h1>
  <p>一个项目从教师登记到验收结题的完整生命周期 —— 科研管理平台(演示)</p>
  <div class="meta">
    <div><b>项目名称</b>高性能阻燃防护面料关键技术攻关与产品开发</div>
    <div><b>项目编号</b>HX2026-001</div>
    <div><b>负责人</b>测试教师 · 智能制造与装备学院</div>
    <div><b>委托单位</b>江苏瑞泰安全防护科技有限公司(企业)</div>
    <div><b>合同经费</b>420,000 元(登记 350,000,变更后 420,000)</div>
    <div><b>到账/核定</b>420,000 元(两期到账,验收核定 420,000)</div>
    <div><b>项目周期</b>2026-09-01 ~ 2027-12-31(变更顺延)</div>
    <div><b>终态</b>已结题</div>
  </div>
</header>
<div class="stat">
  <span class="node">0 草稿</span><span class="arr">→</span>
  <span class="node">1 科技处预审中</span><span class="arr">→</span>
  <span class="node">5 预审通过·待盖章</span><span class="arr">→</span>
  <span class="node">2 已回扫·审核中</span><span class="arr">→</span>
  <span class="node">3 通过</span>
  <span class="note">退回=4(预审/审核任一环节均可退回,教师修改后重新提交);登记/变更/验收三种单据共用同一状态机</span>
</div>
{"".join(sec_html)}
<footer>科研管理平台 · 横向项目流程演示 · 2026-10</footer>
</div>
</body>
</html>"""
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(html)
    print(OUT, f"{os.path.getsize(OUT)/1024/1024:.2f} MB, {no} 步")


if __name__ == "__main__":
    build()
