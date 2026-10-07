#!/bin/bash
# LibreOffice 便携包裁剪(2026-10-07 实测 1537MB→652MB,渲染结果与裁剪前逐字一致)
# 裁剪守则(决策#37):
#   ✂ 只裁与转换无关:他语UI翻译/图标主题/他语词典/剪贴画/模板样例/他语注册配置
#   🔒 绝不裁:program核心dll(mergedlo等)/icudt78.dll(Unicode)/Fonts/soffice.cfg/python-core
#   ✅ 每次裁剪后必须:taskkill soffice → mvn test -Dtest=RenderSmokeTest -Drender.smoke=true 4/4
#      + 与基线 pages.txt 文本对比一致;升级 LibreOffice 版本后重跑本脚本
set -e
LO="$(cd "$(dirname "$0")/.." && pwd)/libreoffice"
[ -d "$LO/program" ] || { echo "未找到 $LO"; exit 1; }

echo "裁剪前: $(du -sm "$LO" | cut -f1) MB"
taskkill //F //IM soffice.bin 2>/dev/null || true
taskkill //F //IM soffice.exe 2>/dev/null || true

# A. UI 语言翻译(仅留 zh-CN/en-US;headless 转换不读 UI)
cd "$LO/program/resource" && ls | grep -v -E "^(zh-CN|en-US)$" | xargs -r rm -rf
# B. 图标主题包(headless 无界面)
rm -f "$LO"/share/config/images_*.zip
# C. 拼写词典(仅留英文;转换不用拼写)
ls "$LO/share/extensions" | grep -E "^dict-" | grep -v "^dict-en$" \
  | while read d; do rm -rf "$LO/share/extensions/$d"; done
# D. 他语注册配置(留 en-US/zh-CN)
cd "$LO/share/registry/res" \
  && ls | grep -E "_langpack_|^registry_" | grep -v -E "(en-US|zh-CN)" | xargs -r rm -f
# E. 剪贴画/模板样例/向导
rm -rf "$LO/share/gallery" "$LO/share/template/common/wizard" "$LO/share/wizards"

echo "裁剪后: $(du -sm "$LO" | cut -f1) MB"
echo "⚠ 现在必须跑冷启动验证:"
echo "  mvn test -Dtest=RenderSmokeTest,PdfSmokeTest -Drender.smoke=true -Dpdf.smoke=true"
