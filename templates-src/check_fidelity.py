# -*- coding: utf-8 -*-
"""
模板保真自检:逐 run 比对纯净版附件 vs 生成的 hx_*.docx,证明"除占位符外零差异"。
硬规则,违例即 FAIL:
  R1 格式不发明:产物每个有字 run 的 rPr 必须来自源文件已有格式(run rPr ∪ 段落标记 rPr)
  R2 结构不动:行对齐后(difflib 文本签名)逐格段落数一致;源被删的行必须是纯空行(固定行设计)
  R3 文本只增占位符:对齐格内文本差异必须含 {{占位符}} 或为选项 run 清空
允许项:body 末尾拖尾空段收敛为 1(LibreOffice 空白页防护);成员/学生表多余空行删除(m1-m6/s1-s4 设计)。
用法:python check_fidelity.py [register|change|acceptance|report]
"""
import difflib
import os
import sys
import zipfile
import xml.etree.ElementTree as ET

W_URI = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
W = '{%s}' % W_URI
ET.register_namespace('w', W_URI)

JOBS = [
    ('附件1：成都轻工职业技术大学横向项目登记表【双面打印，控制在两页内】.docx', 'hx_register.docx'),
    ('附件2：成都轻工职业技术大学横向项目重要事项变更表.docx', 'hx_change.docx'),
    ('附件3（含学生版）：成都轻工职业技术大学横向项目验收登记表【双面打印，控制在两页内】.docx', 'hx_acceptance.docx'),
    ('附件4：横向科研项目验收报告.docx', 'hx_report.docx'),
]


def load(path):
    return ET.fromstring(zipfile.ZipFile(path).read('word/document.xml'))


def para_text(p):
    return ''.join(t.text or '' for t in p.iter(W + 't'))


def row_text(tr):
    """行签名:格文本以|连接;产物中的 {{占位符}} 归零后比较(占位符写入空格不改变行身份)"""
    import re
    joined = '|'.join(para_text(p).strip() for tc in tr.findall(W + 'tc') for p in tc.findall(W + 'p'))
    return re.sub(r'\{\{[^}]*\}\}', '', joined).strip('|')


def rpr_key(rPr):
    if rPr is None:
        return None

    def norm(e):
        return (e.tag, tuple(sorted(e.attrib.items())), tuple(norm(c) for c in e))
    return repr(norm(rPr))


def check(src_path, gen_path):
    src, gen = load(src_path), load(gen_path)
    problems = []

    # R1 格式不发明:源格式集合 = 所有 run rPr ∪ 所有段落标记 rPr(克隆合法来源)
    src_rprs = set()
    for r in src.iter(W + 'r'):
        if list(r.iter(W + 't')):
            src_rprs.add(rpr_key(r.find(W + 'rPr')))
    for p in src.iter(W + 'p'):
        pPr = p.find(W + 'pPr')
        if pPr is not None and pPr.find(W + 'rPr') is not None:
            src_rprs.add(rpr_key(pPr.find(W + 'rPr')))
    invented = []
    for r in gen.iter(W + 'r'):
        text = ''.join(t.text or '' for t in r.iter(W + 't'))
        if not text.strip():
            continue
        if rpr_key(r.find(W + 'rPr')) not in src_rprs:
            invented.append(text[:24])
    if invented:
        problems.append('R1 产物发明了源文件没有的格式 %d 处: %s' % (len(invented), '; '.join(invented[:6])))

    # R2/R3 行级对齐(每张表独立,双指针):容忍两类合法编辑——
    #   ①源独有纯空行(固定行制作删除的多余空行) ②选项格被 {{占位符}} 顶替(行签名随之变短)
    import re
    n_diff = 0

    def norm(s):
        return re.sub(r'\s+', '', s)

    def is_empty_row(tr):
        return not row_text(tr).strip('|').strip()

    def audit_row(cs, cg):
        """纯校验:返回 (issues, placeholder_diffs)。段落数必须一致;
        段文本 规范相等 或 产物含{{占位符}} 或 产物为空(选项run被顶替清空) 均合法"""
        issues, diffs = [], 0
        if len(cs) != len(cg):
            return ['格数不一致 %d→%d' % (len(cs), len(cg))], 0
        for a, b in zip(cs, cg):
            ps_a, ps_b = a.findall(W + 'p'), b.findall(W + 'p')
            if len(ps_a) != len(ps_b):
                issues.append('格内段落数不一致(%d→%d): %r' % (len(ps_a), len(ps_b), para_text(ps_a[0])[:20]))
                continue
            for p_a, p_b in zip(ps_a, ps_b):
                ta, tb = para_text(p_a).strip(), para_text(p_b).strip()
                if norm(ta) == norm(tb):
                    continue
                if '{{' in tb or tb == '':
                    diffs += 1  # 占位符写入/顶替(含选项run清空)
                else:
                    issues.append('文本被改但无占位符: %r → %r' % (ta[:20], tb[:20]))
        return issues, diffs

    for tbl_s, tbl_g in zip(src.iter(W + 'tbl'), gen.iter(W + 'tbl')):
        rows_s, rows_g = tbl_s.findall(W + 'tr'), tbl_g.findall(W + 'tr')
        i = j = 0
        while i < len(rows_s) or j < len(rows_g):
            head_s = row_text(rows_s[i])[:24] if i < len(rows_s) else '<末尾>'
            head_g = row_text(rows_g[j])[:24] if j < len(rows_g) else '<末尾>'
            if i < len(rows_s) and j < len(rows_g):
                issues, diffs = audit_row(rows_s[i].findall(W + 'tc'), rows_g[j].findall(W + 'tc'))
                if not issues:
                    n_diff += diffs
                    i += 1
                    j += 1
                    continue
            if i < len(rows_s) and is_empty_row(rows_s[i]):
                i += 1  # 合法①:源纯空行被删(固定行设计)
            else:
                problems.append('R2 行对不上: 源=%r 产物=%r' % (head_s, head_g))
                i += 1
                j += 1
    return problems, n_diff


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    base = os.path.dirname(os.path.abspath(__file__))
    all_ok = True
    for src_name, gen_name in JOBS:
        if only and only not in gen_name:
            continue
        problems, n_diff = check(os.path.join(base, src_name), os.path.join(base, gen_name))
        print('%s %s (占位符差异段 %d 处)' % ('PASS' if not problems else 'FAIL', gen_name, n_diff))
        for p in problems:
            print('   ', p)
            all_ok = False
    sys.exit(0 if all_ok else 1)


if __name__ == '__main__':
    main()
