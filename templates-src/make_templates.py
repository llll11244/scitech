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


def save(root, out, src, mode='tight'):
    """版式处理分档(三轮迭代定稿,原则:能不动就不动,书写区绝不钳平):
    tight=极限压缩(登记/验收表:2 页硬约束)——边距 900/900/600/600+表格 100% 宽+
          全空格只留 1 段+非空格空段≤2+表内段距清零+行高>800 钳 800(简介/进度行豁免 2000);
    mid=书写行等比缩放(变更表:原版 Word 即 2 页,压回 1 页)——左右边距/表宽/段距/空段全保原版,
          仅上下边距 1440→900,>1000 的大书写行(变更内容 5739/原因 3008/意见栏 ~2000×3)
          按原比例缩放到一页预算(书写区比例不失衡,信息行原样);
    none=原版直出(报告:原版即 1 页,行高 atLeast 随内容自然增长)——版式零改动,仅写占位符。"""
    if mode == 'tight':
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
                else:
                    # 非空格:空段每格至多留 2
                    empty_idx = [i for i, x in enumerate(texts) if not x]
                    for i in empty_idx[2:]:
                        tc.remove(ps[i])
            # 表内段落间距清零(段前后 0、行距单倍)——Word 表格默认间距在 LibreOffice 下更占高
            for p in tbl.iter(W + 'p'):
                pPr = p.find(W + 'pPr')
                if pPr is None:
                    continue
                sp = pPr.find(W + 'spacing')
                if sp is not None:
                    sp.set(W + 'before', '0')
                    sp.set(W + 'after', '0')
                    if sp.get(W + 'line'):
                        sp.set(W + 'line', '240')
                        sp.set(W + 'lineRule', 'auto')
        # 行高钳制:大留白行(>800)压回 800,简介/进度标记行豁免(2000 供内容展开)
        for tr in root.iter(W + 'tr'):
            trPr = tr.find(W + 'trPr')
            if trPr is None:
                continue
            th = trPr.find(W + 'trHeight')
            if th is None:
                continue
            try:
                v = int(th.get(W + 'val', '0'))
            except ValueError:
                continue
            joined = ''.join(t.text or '' for t in tr.iter(W + 't'))
            if v > 800 and not any(k in joined for k in ('项目简介', '项目计划进度')):
                th.set(W + 'val', '800')
                th.set(W + 'hRule', 'atLeast')
    elif mode == 'mid':
        for mg in root.iter(W + 'pgMar'):
            if int(mg.get(W + 'top', '1440')) > 900:
                mg.set(W + 'top', '900')
            if int(mg.get(W + 'bottom', '1440')) > 900:
                mg.set(W + 'bottom', '900')
        # 书写格空段每格至多留 1(16 个空段的"天然最低高度"会顶住行高缩放,必须先降)
        for tbl in root.iter(W + 'tbl'):
            for tc in tbl.iter(W + 'tc'):
                ps = tc.findall(W + 'p')
                texts = [para_text(p).strip() for p in ps]
                if any(texts):
                    empty_idx = [i for i, x in enumerate(texts) if not x]
                    for i in empty_idx[1:]:
                        tc.remove(ps[i])
        # 大书写行(>1000)等比缩放:一页预算 = A4 11906 - 上下边距1800 - 标题区~900 - 信息行~2350 - 余量300
        tall = []
        for tr in root.iter(W + 'tr'):
            trPr = tr.find(W + 'trPr')
            th = trPr.find(W + 'trHeight') if trPr is not None else None
            if th is None:
                continue
            try:
                v = int(th.get(W + 'val', '0'))
            except ValueError:
                continue
            if v > 1000:
                tall.append((th, v))
        if tall:
            budget = 11906 - 1800 - 900 - 2350 - 300
            factor = min(1.0, budget / sum(v for _, v in tall))
            for th, v in tall:
                th.set(W + 'val', str(int(v * factor)))
                th.set(W + 'hRule', 'atLeast')
    # mode == 'none':版式零改动
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


def remove_all_paras(root, predicate):
    """删除所有满足谓词的段落(表内表外都清)——清表尾办理注、意见栏预填文字"""
    for parent in root.iter():
        for c in list(parent):
            if c.tag == W + 'p' and predicate(para_text(c)):
                parent.remove(c)


def clean_cell(root, marker, keep_pred):
    """定位全文含 marker 的单元格,删除 keep_pred 不保留的段落——清同格残留示例/大纲。
    签字/年月日为独立段落,用 keep_pred 显式保留(先探段落结构再清,勿盲删)。"""
    for tc in root.iter(W + 'tc'):
        if marker not in ''.join(t.text or '' for t in tc.iter(W + 't')):
            continue
        for p in list(tc.findall(W + 'p')):
            if not keep_pred(para_text(p)):
                tc.remove(p)
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


def contains_any(*keys):
    def pred(s):
        return any(k in s for k in keys)
    return pred


def find_row(tbl, *keys):
    for tr in rows(tbl):
        texts = [para_text(p) for tc in cells(tr) for p in cell_paras(tc)]
        joined = ' '.join(texts)
        if all(k in joined for k in keys):
            return tr
    return None


def insert_cell(root, row_keys, col_index, ph, overwrite=False):
    """定位含 row_keys 的行,向第 col_index(0起)个单元格插占位符;overwrite=True 覆盖既有提示/选项文字"""
    for tbl in all_tables(root):
        tr = find_row(tbl, *row_keys)
        if tr is not None:
            cs = cells(tr)
            set_cell_placeholder(cs[col_index], ph, overwrite)
            return
    raise RuntimeError('未找到行: %s → 列%d 插 %s' % (row_keys, col_index, ph))


def insert_cell_by_text(root, marker, ph):
    """定位全文含 marker 的单元格,向首段插占位符(纯净版原因格/总结格:首段为空段,签字段在后)"""
    for tc in root.iter(W + 'tc'):
        if marker in ''.join(t.text or '' for t in tc.iter(W + 't')):
            set_cell_placeholder(tc, ph)
            return
    raise RuntimeError('未找到含 %s 的格子' % marker)


def trim_cell_empties(root, marker, keep):
    """定位全文含 marker 的单元格,空段只保留前 keep 个——报告正文格尾随空段收敛"""
    for tc in root.iter(W + 'tc'):
        if marker not in ''.join(t.text or '' for t in tc.iter(W + 't')):
            continue
        for p in list(tc.findall(W + 'p')):
            if not para_text(p).strip():
                if keep > 0:
                    keep -= 1  # 前 keep 个空段保留(格内呼吸留白)
                else:
                    tc.remove(p)
        return


def insert_placeholder_after(root, marker, ph):
    """定位含 marker 的单元格,向 marker 段之后第一个空段插占位符(报告正文格:说明句后留白区开头)"""
    for tc in root.iter(W + 'tc'):
        ps = tc.findall(W + 'p')
        texts = [para_text(p) for p in ps]
        if not any(marker in t for t in texts):
            continue
        for i, t in enumerate(texts):
            if marker in t:
                for q in ps[i + 1:]:
                    if not para_text(q).strip():
                        add_run_text(q, ph)
                        return
                raise RuntimeError('marker 后无空段可插: ' + marker)
    raise RuntimeError('未找到含 marker 的段落: ' + marker)


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
# 附件1 → hx_register.docx(2026-10-07 起基于用户纯净版:示例/字数/表尾注已由用户手工清除)
# ============================================================
def make_register(root):
    replace_first_match(root, contains_all('技术开发', '技术咨询'), '{{projectTypeChecks}}', '项目类型勾选')
    replace_first_match(root, contains_all('年', '月', '至'), '{{startEndDate}}', '起止时间')
    replace_first_match(root, contains_all('名称：'), '名称：{{clientName}}', '委托单位名称')
    replace_first_match(root, contains_all('政府部门', '企业'), '{{clientNatureChecks}}', '性质勾选')
    replace_first_match(root, contains_all('人民币', '大写'),
                        '人民币 {{contractAmount}} 元 （大写：{{contractAmountCapital}}）', '合同经费')
    # 简介/进度:纯净版已清示例,标签旁空值格直接插占位符
    insert_cell(root, ('项目简介',), 1, '{{projectSummary}}')
    insert_cell(root, ('项目计划进度',), 1, '{{progressPlan}}')
    # 设备/管理费:标签+内联选项同段——保留标签,只把选项部分换成占位符(值=纯勾选串)
    replace_first_match(root, contains_all('学校仪器设备'),
                        '项目是否以使用学校仪器设备和场地为主：{{useSchoolAssetChecks}}', '设备场地勾选')
    replace_first_match(root, contains_all('是否收取管理费'),
                        '是否收取管理费：{{chargeMgmtFeeChecks}}', '管理费勾选')
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
# 附件2 → hx_change.docx(基于用户纯净版:示例/预填/表尾注已由用户手工清除)
# ============================================================
def make_change(root):
    insert_cell(root, ('项目名称',), 1, '{{projectName}}')
    insert_cell(root, ('项目编号', '项目类别'), 1, '{{approvalNo}}')
    insert_cell(root, ('项目编号', '项目类别'), 3, '{{projectTypeChecks}}', overwrite=True)
    insert_cell(root, ('项目负责人', '联系方式'), 1, '{{leaderName}}')
    insert_cell(root, ('项目负责人', '联系方式'), 3, '{{leaderPhone}}')
    # 变更内容勾选行(含"项目经费"等选项)与空白的变更内容行同名:勾选行按选项关键词定位,
    # 空白行=整行文字仅"变更内容"标签(精确匹配,避免替换后的勾选行被误配)
    insert_cell(root, ('变更内容', '项目经费', '其他约定'), 1, '{{changeTypeChecks}}', overwrite=True)
    for tbl in all_tables(root):
        for tr in rows(tbl):
            joined = ''.join(para_text(p) for tc in cells(tr) for p in cell_paras(tc))
            if joined.strip() == '变更内容':
                set_cell_placeholder(cells(tr)[1], '{{changeContent}}')
                break
    # 原因格:首段空段直插(签字段在后,保留)
    insert_cell_by_text(root, '申请人（签字）', '{{changeReason}}')


# ============================================================
# 附件3 → hx_acceptance.docx(基于用户纯净版:举例/预填/表头提示/表尾注已由用户清除)
# ============================================================
def make_acceptance(root):
    insert_cell(root, ('项目编号', '项目名称'), 1, '{{approvalNo}}')
    insert_cell(root, ('项目编号', '项目名称'), 3, '{{projectName}}')
    replace_first_match(root, contains_all('技术开发', '技术咨询'), '{{projectTypeChecks}}', '项目类型勾选')
    replace_first_match(root, contains_all('年', '月', '至'), '{{startEndDate}}', '起止时间')
    insert_cell(root, ('委托单位', '名称'), 2, '{{clientName}}')
    replace_first_match(root, contains_all('政府部门', '企业'), '{{clientNatureChecks}}', '性质勾选')
    insert_cell(root, ('到账经费', '使用经费'), 1, '{{receivedAmountWan}}')
    insert_cell(root, ('到账经费', '使用经费'), 3, '{{usedAmountWan}}')
    insert_cell(root, ('项目负责人', '所在院部'), 1, '{{leaderName}}')
    insert_cell(root, ('项目负责人', '所在院部'), 3, '{{deptName}}')
    insert_cell(root, ('完成时间',), 1, '{{finishDate}}')
    insert_cell(root, ('成果形式', '技术报告'), 1, '{{outcomeFormChecks}}', overwrite=True)
    insert_cell(root, ('到账金额核定',), 2, '{{verifiedAmountWan}}')
    # 总结格:首段空段直插(其后留白段与签字段保留)
    insert_cell_by_text(root, '项目负责人（签字）', '{{acceptSummary}}')

    make_fixed_rows(root, ('姓名', '所在院部', '职称', '承担本项目的工作任务比例'),
                    ['', 'name', 'unit', 'title', 'major', 'dutyRatio'], 8, 'm',
                    stop_keys=('学生姓名',))  # 空行清理到学生表头即停,防越区误删
    make_fixed_rows(root, ('学生姓名', '学号', '指导老师'),
                    ['', 'name', 'studentNo', 'major', 'mentor', 'duty'], 4, 's')


# ============================================================
# 附件4 → hx_report.docx(基于用户纯净版:大纲/参考说明已由用户清除,
# 说明句"（包括项目成果内容提要…页数不限。）"按用户保留)
# ============================================================
def make_report(root):
    replace_first_match(root, contains_all('项目编号：'), '项目编号：{{approvalNo}}', '项目编号')
    replace_first_match(root, contains_all('项目名称：'), '项目名称：{{projectName}}', '项目名称')
    replace_first_match(root, contains_all('是否按期完成'), '是否按期完成：{{onTimeChecks}}', '按期完成勾选')
    replace_first_match(root, contains_all('项目类型：'), '项目类型：{{projectTypeChecks}}', '项目类型勾选')
    replace_first_match(root, contains_all('成果形式（可多选）'), '成果形式（可多选）：{{outcomeFormChecks}}', '成果形式勾选')
    # 正文:说明句后的第一个空段插入段落级区块对循环(poi-tl 区块对支持段落重复)
    insert_placeholder_after(root, '页数不限', '{{?reportParagraphs}}{{this}}{{/reportParagraphs}}')
    # 尾随空段收敛至 5:原 20 个空段在 LibreOffice 下会把签字行顶到第 2 页孤页
    # (Word 里无此问题——行高 atLeast 4942 保箱体;此处只删空段,行高不动)
    trim_cell_empties(root, '页数不限', 5)


SRC_DIR = os.path.dirname(os.path.abspath(__file__))
JOBS = [
    ('附件1：成都轻工职业技术大学横向项目登记表【双面打印，控制在两页内】.docx', 'hx_register.docx', make_register, 'tight'),
    ('附件2：成都轻工职业技术大学横向项目重要事项变更表.docx', 'hx_change.docx', make_change, 'mid'),
    ('附件3（含学生版）：成都轻工职业技术大学横向项目验收登记表【双面打印，控制在两页内】.docx', 'hx_acceptance.docx', make_acceptance, 'tight'),
    ('附件4：横向科研项目验收报告.docx', 'hx_report.docx', make_report, 'none'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    for src_name, out_name, fn, mode in JOBS:
        if only and only not in out_name:
            continue
        src = os.path.join(SRC_DIR, src_name)
        out = os.path.join(SRC_DIR, out_name)
        root = load(src)
        fn(root)
        # C类:表尾办理说明统一清除(净版源已无,防御性保留;文案迁 templates-src/填写提示.md)
        remove_all_paras(root, contains_any('一式四份', '一式三份', '骑缝章', '签名处须手写'))
        save(root, out, src, mode)
        print('OK', out_name)


if __name__ == '__main__':
    main()
