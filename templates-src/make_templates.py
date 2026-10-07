# -*- coding: utf-8 -*-
"""
把 4 张原始 docx 制作成 poi-tl 模板(占位符 {{key}},行循环行首 {{members}} 等)。
策略:
  1) 段落级替换:整段文本满足谓词 → 重写为占位符(规避 Word 跨 run 拆字)
  2) 空格插值:按「行首列文本+列序」定位表格单元格,插入占位符
  3) 行循环:成员/学生表第一数据行改循环模板行,删多余空行
输出:templates-src/hx_{register,change,acceptance,report}.docx
"""
import copy
import os
import shutil
import sys
import zipfile
import xml.etree.ElementTree as ET

W_URI = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
W = '{%s}' % W_URI
XML_SPACE = '{http://www.w3.org/XML/1998/namespace}space'

# 保持 Word 原前缀
for pfx, uri in [
    ('w', W_URI),
    ('r', 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'),
    ('wp', 'http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing'),
    ('mc', 'http://schemas.openxmlformats.org/markup-compatibility/2006'),
    ('wps', 'http://schemas.microsoft.com/office/word/2010/wordprocessingShape'),
    ('a', 'http://schemas.openxmlformats.org/drawingml/2006/main'),
    ('pic', 'http://schemas.openxmlformats.org/drawingml/2006/picture'),
    ('wpc', 'http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas'),
    ('o', 'urn:schemas-microsoft-com:office:office'),
    ('v', 'urn:schemas-microsoft-com:vml'),
    ('w10', 'urn:schemas-microsoft-com:office:word'),
    ('sl', 'http://schemas.openxmlformats.org/schemaLibrary/2006/main'),
    ('w14', 'http://schemas.microsoft.com/office/word/2010/wordml'),
    ('w15', 'http://schemas.microsoft.com/office/word/2012/wordml'),
    ('wpg', 'http://schemas.microsoft.com/office/word/2010/wordprocessingGroup'),
    ('wpi', 'http://schemas.microsoft.com/office/word/2010/wordprocessingInk'),
    ('wne', 'http://schemas.microsoft.com/office/word/2006/wordml'),
    ('cx', 'http://schemas.microsoft.com/office/drawing/2014/chart'),
]:
    ET.register_namespace(pfx, uri)


FONT_SONG = '宋体'


def _set_run_font(r):
    """run 强制中文字体(宋体含 ☑/□ 字形,避免回退乱码)"""
    rPr = r.find(W + 'rPr')
    if rPr is None:
        rPr = ET.Element(W + 'rPr')
        r.insert(0, rPr)
    fonts = rPr.find(W + 'rFonts')
    if fonts is None:
        fonts = ET.SubElement(rPr, W + 'rFonts')
    for attr in ('ascii', 'eastAsia', 'hAnsi', 'cs'):
        fonts.set(W + attr, FONT_SONG)


def add_run_text(p, text):
    r = ET.SubElement(p, W + 'r')
    _set_run_font(r)
    t = ET.SubElement(r, W + 't')
    t.text = text
    t.set(XML_SPACE, 'preserve')


def para_text(p):
    return ''.join(t.text or '' for t in p.iter(W + 't'))


def replace_para(p, new_text):
    """段落全文重写为 new_text:首个 run 保格式重写,其余清空;
    若首 run 无中文字体设置则补宋体(勾选组/大写金额含 ☑ 等特殊字形)"""
    ts = list(p.iter(W + 't'))
    if not ts:
        add_run_text(p, new_text)
        return
    ts[0].text = new_text
    ts[0].set(XML_SPACE, 'preserve')
    for t in ts[1:]:
        t.text = ''
    r = ts[0].getparent() if hasattr(ts[0], 'getparent') else None
    while r is not None and r.tag != W + 'r':
        r = r.getparent() if hasattr(r, 'getparent') else None
    if r is not None:
        _set_run_font(r)


def add_run_text(p, text):
    r = ET.SubElement(p, W + 'r')
    t = ET.SubElement(r, W + 't')
    t.text = text
    t.set(XML_SPACE, 'preserve')


def cell_paras(tc):
    return tc.findall(W + 'p')


def set_cell_placeholder(tc, text, overwrite=False):
    """往单元格第一段插入占位符文本;overwrite=True 时覆盖既有提示文字"""
    ps = cell_paras(tc)
    if not ps:
        p = ET.SubElement(tc, W + 'p')
        add_run_text(p, text)
        return
    if para_text(ps[0]).strip():
        if not overwrite:
            raise RuntimeError('格子里已有文字,不宜插占位符: ' + para_text(ps[0])[:30])
        replace_para(ps[0], text)
        for extra in ps[1:]:
            tc.remove(extra)
        return
    add_run_text(ps[0], text)


def load(path):
    with zipfile.ZipFile(path) as z:
        return ET.fromstring(z.read('word/document.xml'))


def save(root, out, src):
    # 版式收紧,压回两页内(原 .doc 排版在 LibreOffice 下空表即 3 页):
    # ①页边距 1800→900/上下 600 ②表格 100% 自适应 ③空格子只留 1 段 ④表尾注释 9pt
    for mg in root.iter(W + 'pgMar'):
        mg.set(W + 'left', '900')
        mg.set(W + 'right', '900')
        if int(mg.get(W + 'top', '1440')) > 600:
            mg.set(W + 'top', '600')
        if int(mg.get(W + 'bottom', '1440')) > 600:
            mg.set(W + 'bottom', '600')
    for tbl in root.iter(W + 'tbl'):
        tblPr = tbl.find(W + 'tblPr')
        if tblPr is not None:
            tw = tblPr.find(W + 'tblW')
            if tw is not None:
                tw.set(W + 'w', '5000')
                tw.set(W + 'type', 'pct')
            layout = tblPr.find(W + 'tblLayout')
            if layout is None:
                layout = ET.SubElement(tblPr, W + 'tblLayout')
            layout.set(W + 'type', 'autofit')
        for tc in tbl.iter(W + 'tc'):
            ps = tc.findall(W + 'p')
            texts = [para_text(p).strip() for p in ps]
            if not any(texts):
                # 全空格子:只留一段(多余空段占行高)
                for extra in ps[1:]:
                    tc.remove(extra)
    # 表格外段落(表尾说明)缩到 9pt
    body = root.find(W + 'body')
    if body is not None:
        for p in [c for c in body if c.tag == W + 'p']:
            if not para_text(p).strip():
                continue
            for r in p.iter(W + 'r'):
                rPr = r.find(W + 'rPr')
                if rPr is None:
                    rPr = ET.Element(W + 'rPr')
                    r.insert(0, rPr)
                sz = rPr.find(W + 'sz')
                if sz is None:
                    sz = ET.SubElement(rPr, W + 'sz')
                sz.set(W + 'val', '18')
                szCs = rPr.find(W + 'szCs')
                if szCs is None:
                    szCs = ET.SubElement(rPr, W + 'szCs')
                szCs.set(W + 'val', '18')
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.namelist():
            if item == 'word/document.xml':
                zout.writestr(item, ET.tostring(root, encoding='UTF-8', xml_declaration=True))
            else:
                zout.writestr(item, zin.read(item))


def all_paragraphs(root):
    return list(root.iter(W + 'p'))


def all_tables(root):
    return list(root.iter(W + 'tbl'))


def rows(tbl):
    return tbl.findall(W + 'tr')


def cells(tr):
    return tr.findall(W + 'tc')


def replace_first_match(root, predicate, new_text, label):
    for p in all_paragraphs(root):
        if predicate(para_text(p)):
            replace_para(p, new_text)
            return True
    raise RuntimeError('未找到待替换段落: ' + label)


def remove_first_para(root, predicate):
    """删除首个满足谓词的段落(幂等,删不到不报错)——清『示例：』『举例：』等残留标题段"""
    for parent in root.iter():
        for c in list(parent):
            if c.tag == W + 'p' and predicate(para_text(c)):
                parent.remove(c)
                return


def replace_span(root, start_pred, new_text, label, end_pred=lambda s: False):
    """start_pred 命中段重写为 new_text,其后同容器段落删到 end_pred(保留)——清示例残留"""
    for parent in root.iter():
        ps = [c for c in parent if c.tag == W + 'p']
        for i, p in enumerate(ps):
            if start_pred(para_text(p)):
                replace_para(p, new_text)
                for q in ps[i + 1:]:
                    if end_pred(para_text(q)):
                        return True
                    parent.remove(q)
                return True
    raise RuntimeError('未找到待替换段落: ' + label)


def contains_all(*keys):
    def pred(s):
        return all(k in s for k in keys)
    return pred


def find_row(tbl, *keys):
    for tr in rows(tbl):
        texts = [para_text(p) for tc in cells(tr) for p in cell_paras(tc)]
        joined = ' '.join(texts)
        if all(k in joined for k in keys):
            return tr
    return None


def insert_cell(root, row_keys, col_index, ph):
    """定位含 row_keys 的行,向第 col_index(0起)个单元格插占位符"""
    for tbl in all_tables(root):
        tr = find_row(tbl, *row_keys)
        if tr is not None:
            cs = cells(tr)
            set_cell_placeholder(cs[col_index], ph)
            return
    raise RuntimeError('未找到行: %s → 列%d 插 %s' % (row_keys, col_index, ph))


def make_fixed_rows(root, header_keys, fields, row_count, prefix, stop_keys=()):
    """表头行后 row_count 行改为固定占位符行:第 i 行各列填 {{prefix i+字段}},
    提示文字(XX万元/请自行增减行等)清空。不做行删除/复制——规避 poi-tl 行循环
    在 vMerge 大表上删行引发 XmlValueDisconnectedException 的坑,且版式与原表一致。
    stop_keys:下一区段表头关键词——空行清理到该行即停(防越区段误删)。"""
    for tbl in all_tables(root):
        tr = find_row(tbl, *header_keys)
        if tr is None:
            continue
        trs = rows(tbl)
        idx = trs.index(tr)
        cand = [r for r in trs[idx + 1:] if len(cells(r)) >= len(fields)]
        if len(cand) < row_count:
            row_count = len(cand)
        for i in range(row_count):
            cs = cells(cand[i])
            for j, field in enumerate(fields):
                if not field:
                    continue  # 首列 vMerge 标签列不放占位符
                set_cell_placeholder(cs[j], '{{%s%d%s}}' % (prefix, i + 1, field), overwrite=True)
        # 多余纯空数据行删除(制作期静态删;遇下一区段表头即停)
        filled = set(id(c) for c in cand[:row_count])
        for extra in cand[row_count:]:
            joined = ''.join(para_text(p) for tc in cells(extra) for p in cell_paras(tc))
            if any(k in joined for k in stop_keys):
                break
            if id(extra) not in filled and joined.strip() == '':
                tbl.remove(extra)
        return
    raise RuntimeError('未找到固定行表: %s' % (header_keys,))


# ============================================================
# 附件1 → hx_register.docx
# ============================================================
def make_register(root):
    replace_first_match(root, contains_all('技术开发', '技术咨询'), '{{projectTypeChecks}}', '项目类型勾选')
    replace_first_match(root, contains_all('年', '月', '至'), '{{startEndDate}}', '起止时间')
    replace_first_match(root, contains_all('名称：'), '名称：{{clientName}}', '委托单位名称')
    replace_first_match(root, contains_all('政府部门', '企业'), '{{clientNatureChecks}}', '性质勾选')
    replace_first_match(root, contains_all('人民币', '大写'),
                        '人民币 {{contractAmount}} 元 （大写：{{contractAmountCapital}}）', '合同经费')
    replace_span(root, contains_all('示例（要求信息完整）'), '{{projectSummary}}', '项目简介')
    remove_first_para(root, lambda s: s.strip() in ('示例：', '示例:'))
    replace_span(root, contains_all('2025年X月'), '{{progressPlan}}', '项目计划进度')
    replace_first_match(root, contains_all('学校仪器设备'), '{{useSchoolAssetChecks}}', '设备场地勾选')
    replace_first_match(root, contains_all('是否收取管理费：'), '{{chargeMgmtFeeChecks}}', '管理费勾选')
    replace_first_match(root, contains_all('收取管理费金额'), '收取管理费金额：{{mgmtFeeAmount}} 元', '管理费金额')
    replace_first_match(root, contains_all('立项登记日期'), '立项登记日期  {{hxRegisterDate}}', '登记日期')

    insert_cell(root, ('项目编号', '项目名称'), 1, '{{approvalNo}}')
    insert_cell(root, ('项目编号', '项目名称'), 3, '{{projectName}}')
    insert_cell(root, ('项目负责人', '所属院部'), 1, '{{leaderName}}')
    insert_cell(root, ('项目负责人', '所属院部'), 3, '{{deptName}}')
    insert_cell(root, ('联系电话', '电子邮箱'), 1, '{{leaderPhone}}')
    insert_cell(root, ('联系电话', '电子邮箱'), 3, '{{leaderEmail}}')

    # 预算 6 行固定占位符(业务费/劳务费/绩效/业务接待费/协作费/其它)
    for key, ph in [('业务费', '{{budgetBusiness}}'), ('劳务费', '{{budgetLabor}}'), ('绩效', '{{budgetPerformance}}'),
                    ('业务接待费', '{{budgetReception}}'), ('协作费', '{{budgetCollab}}'), ('其它', '{{budgetOther}}')]:
        for tbl in all_tables(root):
            tr = find_row(tbl, key)
            if tr is not None and '元' not in ''.join(
                    para_text(p) for tc in cells(tr) for p in cell_paras(tc)):
                cs = cells(tr)
                if len(cs) >= 2 and para_text(cell_paras(cs[0])[0]).strip() == key:
                    set_cell_placeholder(cs[1], ph)
                    break
        else:
            continue
        continue

    make_fixed_rows(root, ('姓名', '所在单位', '职称', '专业', '承担本项目的工作任务'),
                    ['', 'name', 'unit', 'title', 'major', 'duty'], 6, 'm')
    # 简介/进度格固定行高压缩(200-300 字按压缩行宽 4~5 行足够,原留白过大)
    shrink_row_height(root, '项目简介', 2000)
    shrink_row_height(root, '项目计划进度', 2000)


def shrink_row_height(root, row_key, new_height):
    """按行文本定位,压固定行高(简介/进度格原留白 3800/3250 twips 过大)"""
    for tbl in all_tables(root):
        tr = find_row(tbl, row_key)
        if tr is None:
            continue
        trPr = tr.find(W + 'trPr')
        if trPr is None:
            trPr = ET.Element(W + 'trPr')
            tr.insert(0, trPr)
        th = trPr.find(W + 'trHeight')
        if th is None:
            th = ET.SubElement(trPr, W + 'trHeight')
        th.set(W + 'val', str(new_height))
        th.set(W + 'hRule', 'atLeast')
        return


# ============================================================
# 附件2 → hx_change.docx
# ============================================================
def make_change(root):
    replace_first_match(root, contains_all('技术开发', '技术咨询'), '{{projectTypeChecks}}', '项目类型勾选')
    replace_first_match(root, contains_all('项目经费', '执行周期', '其他约定'), '{{changeTypeChecks}}', '变更内容勾选')
    replace_first_match(root, contains_all('对合同约定的关键内容进行调整'), '{{changeContent}}', '变更内容说明')
    replace_first_match(root, contains_all('示例：因涉及后续收益'), '{{changeReason}}', '变更原因')

    insert_cell(root, ('项目名称',), 1, '{{projectName}}')
    insert_cell(root, ('项目编号', '项目类别'), 1, '{{approvalNo}}')
    insert_cell(root, ('项目负责人', '联系方式'), 1, '{{leaderName}}')
    insert_cell(root, ('项目负责人', '联系方式'), 3, '{{leaderPhone}}')


# ============================================================
# 附件3 → hx_acceptance.docx
# ============================================================
def make_acceptance(root):
    replace_first_match(root, contains_all('技术开发', '技术咨询'), '{{projectTypeChecks}}', '项目类型勾选')
    replace_first_match(root, contains_all('年', '月', '至'), '{{startEndDate}}', '起止时间')
    replace_first_match(root, contains_all('政府部门', '企业'), '{{clientNatureChecks}}', '性质勾选')
    replace_first_match(root, contains_all('成果形式'), '{{outcomeFormChecks}}', '成果形式勾选')
    remove_first_para(root, lambda s: s.strip() in ('举例：', '举例:'))
    replace_span(root, contains_all('目标达成情况'), '{{acceptSummary}}', '完成情况总结',
                 end_pred=lambda s: '项目负责人（签字）' in s)

    insert_cell(root, ('项目编号', '项目名称'), 1, '{{approvalNo}}')
    insert_cell(root, ('项目编号', '项目名称'), 3, '{{projectName}}')
    insert_cell(root, ('委托单位', '名称'), 2, '{{clientName}}')
    insert_cell(root, ('到账经费', '使用经费'), 1, '{{receivedAmountWan}}')
    insert_cell(root, ('到账经费', '使用经费'), 3, '{{usedAmountWan}}')
    insert_cell(root, ('项目负责人', '所在院部'), 1, '{{leaderName}}')
    insert_cell(root, ('项目负责人', '所在院部'), 3, '{{deptName}}')
    insert_cell(root, ('完成时间',), 1, '{{finishDate}}')
    insert_cell(root, ('到账金额核定',), 2, '{{verifiedAmountWan}}')

    make_fixed_rows(root, ('姓名', '所在院部', '职称', '承担本项目的工作任务比例'),
                    ['', 'name', 'unit', 'title', 'major', 'dutyRatio'], 8, 'm',
                    stop_keys=('学生姓名',))
    make_fixed_rows(root, ('学生姓名', '学号', '指导老师'),
                    ['', 'name', 'studentNo', 'major', 'mentor', 'duty'], 4, 's')


# ============================================================
# 附件4 → hx_report.docx
# ============================================================
def make_report(root):
    replace_first_match(root, contains_all('项目编号：'), '项目编号：{{approvalNo}}', '项目编号')
    replace_first_match(root, contains_all('项目名称：'), '项目名称：{{projectName}}', '项目名称')
    replace_first_match(root, contains_all('是否按期完成'), '是否按期完成：{{onTimeChecks}}', '按期完成勾选')
    replace_first_match(root, contains_all('项目类型：'), '项目类型：{{projectTypeChecks}}', '项目类型勾选')
    replace_first_match(root, contains_all('成果形式（可多选）'), '成果形式（可多选）：{{outcomeFormChecks}}', '成果形式勾选')
    # 正文:整格大段提示 → 段落级区块对循环(poi-tl 区块对支持段落重复)
    replace_first_match(root, contains_all('一、项目目标达成情况'), '{{?reportParagraphs}}{{this}}{{/reportParagraphs}}', '正文提示')


SRC_DIR = os.path.dirname(os.path.abspath(__file__))
JOBS = [
    ('附件1：成都轻工职业技术大学横向项目登记表【双面打印，控制在两页内】.docx', 'hx_register.docx', make_register),
    ('附件2：成都轻工职业技术大学横向项目重要事项变更表.docx', 'hx_change.docx', make_change),
    ('附件3（含学生版）：成都轻工职业技术大学横向项目验收登记表【双面打印，控制在两页内】.docx', 'hx_acceptance.docx', make_acceptance),
    ('附件4：横向科研项目验收报告.docx', 'hx_report.docx', make_report),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    for src_name, out_name, fn in JOBS:
        if only and only not in out_name:
            continue
        src = os.path.join(SRC_DIR, src_name)
        out = os.path.join(SRC_DIR, out_name)
        root = load(src)
        fn(root)
        save(root, out, src)
        print('OK', out_name)


if __name__ == '__main__':
    main()
