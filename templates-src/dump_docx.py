# -*- coding: utf-8 -*-
"""dump docx 结构:按文档顺序输出段落与表格(行|单元格),供占位符设计用"""
import sys
import zipfile
import xml.etree.ElementTree as ET

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'


def para_text(p):
    return ''.join(t.text or '' for t in p.iter(W + 't'))


def walk(elem, depth=0):
    for child in elem:
        tag = child.tag
        if tag == W + 'p':
            txt = para_text(child)
            if txt.strip():
                print('P:', txt)
        elif tag == W + 'tbl':
            print('=== TABLE ===')
            for tr in child.findall(W + 'tr'):
                cells = []
                for tc in tr.findall(W + 'tc'):
                    cell = ' '.join(para_text(p) for p in tc.findall(W + 'p'))
                    cells.append(cell.strip())
                print(' | '.join(cells))
            print('=== /TABLE ===')


def main(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read('word/document.xml')
    root = ET.fromstring(xml)
    body = root.find(W + 'body')
    walk(body)


if __name__ == '__main__':
    main(sys.argv[1])
