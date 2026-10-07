package com.fz.scitech.platform.template;

import org.jodconverter.local.office.LocalOfficeManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渲染管线核心测试(计划 Task 12 Step 3/4):
 * mvn test -Dtest=RenderPipelineTest -Drender.smoke=true
 * ① 四类模板样例渲染页数断言(三类≤2,报告类≥1)
 * ② 失败路径:损坏模板(占位符在但 XML 结构坏)→ 抛异常且临时目录无 render-* 残留
 */
@EnabledIfSystemProperty(named = "render.smoke", matches = "true")
class RenderPipelineTest {

    private static LocalOfficeManager manager;
    private static PdfRenderService renderService;
    private static TemplateService templateService;
    private static PdfConverter converter;

    @BeforeAll
    static void setUp() {
        manager = LocalOfficeManager.builder()
                .officeHome(new File("../libreoffice"))
                .portNumbers(2002)
                .processTimeout(30_000L)
                .taskExecutionTimeout(60_000L)
                .build();
        try {
            manager.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        OfficeManagerConfig cfg = new OfficeManagerConfig();
        cfg.inject(manager);
        converter = new PdfConverter(cfg);
        templateService = new TemplateService(null, converter, "target/test-upload");
        renderService = new PdfRenderService(templateService, converter, null, "target/test-upload");
    }

    @AfterAll
    static void tearDown() throws Exception {
        try {
            manager.stop();
        } catch (Exception ignored) {
        }
    }

    private void assertPages(String tplType, int min, int max) throws Exception {
        Map<String, Object> model = SampleData.forType(tplType);
        File tpl = new File("../templates-src/" + tplType + ".docx");
        PdfRenderService.TempRender r = renderService.renderToTemp(tplType, tpl, model, "test", "tester");
        Files.deleteIfExists(r.pdf());
        assertTrue(r.pages() >= min && r.pages() <= max, tplType + " 页数 " + r.pages() + " 不在 [" + min + "," + max + "]");
        System.out.println(tplType + " pages=" + r.pages());
        assertEquals(0, countTempResidue(), "渲染后临时目录不应有残留");
    }

    @Test
    void registerWithinPageLimit() throws Exception {
        assertPages("hx_register", 1, TemplatePlaceholders.maxPages("hx_register"));
    }

    @Test
    void changeWithinPageLimit() throws Exception {
        assertPages("hx_change", 1, TemplatePlaceholders.maxPages("hx_change"));
    }

    @Test
    void acceptanceWithinPageLimit() throws Exception {
        assertPages("hx_acceptance", 1, TemplatePlaceholders.maxPages("hx_acceptance"));
    }

    @Test
    void reportUnlimited() throws Exception {
        assertPages("hx_report", 1, 99);
    }

    @Test
    void brokenTemplateThrowsAndCleansUp() throws Exception {
        // 构造坏模板:占位符齐全,但表格 XML 闭合标签被删(XWPFTemplate 解析必败)
        Path bad = buildBrokenTemplate();
        try {
            assertThrows(Exception.class, () ->
                    renderService.renderToTemp("hx_change", bad.toFile(), SampleData.forType("hx_change"), "t", "t"));
            assertEquals(0, countTempResidue(), "失败路径也不应留临时残留");
        } finally {
            Files.deleteIfExists(bad);
        }
    }

    @Test
    void renderDualOutputDocxMainAndPdfHidden() throws Exception {
        // 直出 Word 决策(2026-10-07):render() 双出——docx 主交付(hx_docx) + PDF 副产物(hx_pdf),
        // 返回 docx 附件 id;模板/附件 mapper 均 Mockito 桩
        Files.createDirectories(Path.of("target/test-upload"));
        // 清理本用例历史落盘(否则重跑累计 pending_* 对,恰好1个的断言必挂)
        Path histDir = Path.of("target/test-upload/hx");
        if (Files.exists(histDir)) {
            try (Stream<Path> walk = Files.walk(histDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.copy(Path.of("../templates-src/hx_change.docx"),
                Path.of("target/test-upload/test-tpl.docx"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        com.fz.scitech.platform.template.mapper.SysTemplateMapper tplMapper =
                org.mockito.Mockito.mock(com.fz.scitech.platform.template.mapper.SysTemplateMapper.class);
        org.mockito.Mockito.when(tplMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    com.fz.scitech.platform.template.entity.SysTemplate t =
                            new com.fz.scitech.platform.template.entity.SysTemplate();
                    t.setId(1L);
                    t.setTplType("hx_change");
                    t.setFilePath("test-tpl.docx");
                    t.setStatus(1);
                    return t;
                });
        com.fz.scitech.platform.attachment.mapper.RpAttachmentMapper stubMapper =
                org.mockito.Mockito.mock(com.fz.scitech.platform.attachment.mapper.RpAttachmentMapper.class);
        org.mockito.Mockito.when(stubMapper.insert(org.mockito.ArgumentMatchers.any(
                        com.fz.scitech.platform.attachment.RpAttachment.class)))
                .thenAnswer(inv -> {
                    inv.getArgument(0, com.fz.scitech.platform.attachment.RpAttachment.class).setId(9L);
                    return 1;
                });
        TemplateService svcTpl = new TemplateService(tplMapper, converter, "target/test-upload");
        PdfRenderService svc = new PdfRenderService(svcTpl, converter, stubMapper, "target/test-upload");
        com.fz.scitech.platform.template.dto.RenderContext ctx = new com.fz.scitech.platform.template.dto.RenderContext(
                88L, 1L, "change", "hx_change", 7L, "tester", false);
        Long docxId = svc.render(ctx, SampleData.forType("hx_change"));
        assertEquals(9L, docxId, "应返回 docx 主交付附件 id");
        org.mockito.ArgumentCaptor<com.fz.scitech.platform.attachment.RpAttachment> cap =
                org.mockito.ArgumentCaptor.forClass(com.fz.scitech.platform.attachment.RpAttachment.class);
        org.mockito.Mockito.verify(stubMapper, org.mockito.Mockito.times(2)).insert(cap.capture());
        // Task21 stem 推导:非 hx_report 流程维持 hx_docx/hx_pdf(finalVersion 前缀 hx_final_ 另论)
        assertEquals(java.util.List.of("hx_docx", "hx_pdf"),
                cap.getAllValues().stream().map(com.fz.scitech.platform.attachment.RpAttachment::getBizType)
                        .sorted().collect(java.util.stream.Collectors.toList()),
                "biz_type 应按 stem(ctx,ext) 推导");
        File dir = new File("target/test-upload/hx/1/change/88");
        String[] docx = dir.list((d, n) -> n.startsWith("pending_") && n.endsWith(".docx"));
        String[] pdf = dir.list((d, n) -> n.startsWith("pending_") && n.endsWith(".pdf"));
        assertTrue(docx != null && docx.length == 1, "应落盘 1 个 pending_*.docx");
        assertTrue(pdf != null && pdf.length == 1, "应落盘 1 个 pending_*.pdf(隐藏副产物)");
        // Task21 文件名 tplType 段:同流程同秒多模板渲染防互相覆盖
        assertTrue(docx[0].endsWith("_hx_change.docx") && pdf[0].endsWith("_hx_change.pdf"),
                "文件名应含 tplType 段:" + docx[0] + "/" + pdf[0]);
    }

    @Test
    void dynamicRowsPresetKeptAndExpandedBeyond() throws Exception {
        // 动态行决策(2026-10-07):登记表预置 m1-m10(原版 10 空行)——
        // ≤10 条:模板原样渲染,空行保留不裁;>10 条:渲染前克隆末行动态行扩行,克隆行格式与源行一致
        File tpl = new File("../templates-src/hx_register.docx");

        // ① ≤预置:纯样例(3 条成员),行数=模板原版 32 行,尾部空行保留
        Path out1 = Path.of("target/test-rows-preset.docx");
        templateService.renderDocx("hx_register", tpl, out1.toFile(), SampleData.forType("hx_register"));
        String xml1 = docXml(out1);
        assertEquals(32, countRows(xml1), "预置行数下不得增删行(空行保留)");
        assertTrue(!xml1.contains("{{"), "占位符应全部渲掉");

        // ② >预置:12 条连续成员(m1-m12 全有值,渲染端按条数连续平铺是契约),扩 2 行(共 34 行),
        //    m11/m12 值出现,克隆行列宽与数据行一致
        Map<String, Object> model = SampleData.forType("hx_register");
        for (int i = 4; i <= 12; i++) {
            model.put("m" + i + "name", "成员" + i);
            model.put("m" + i + "unit", "测试学院");
            model.put("m" + i + "title", "讲师");
            model.put("m" + i + "major", "机械工程");
            model.put("m" + i + "duty", "扩行验证");
        }
        Path out2 = Path.of("target/test-rows-expanded.docx");
        templateService.renderDocx("hx_register", tpl, out2.toFile(), model);
        String xml2 = docXml(out2);
        assertEquals(34, countRows(xml2), "12 条成员应扩 2 行");
        assertTrue(xml2.contains("成员11") && xml2.contains("成员12"), "扩行数据应渲染");
        assertTrue(!xml2.contains("{{"), "占位符应全部渲掉");
        assertEquals(colWidths(rowOf(xml1, "李四")), colWidths(rowOf(xml2, "成员11")),
                "克隆行列宽应与数据行一致(排版保真)");

        // ③ 扩行结果过完整管线:renderToTemp(含 LibreOffice docx→PDF 转换+页数校验),vMerge 延伸后可正常转换
        PdfRenderService.TempRender tr = renderService.renderToTemp("hx_register", tpl, model, "expand-test", "tester");
        try {
            assertTrue(tr.pdf().toFile().length() > 10_000 && tr.pages() >= 1, "扩行 docx 应可转 PDF");
            System.out.println("扩行渲染 pages=" + tr.pages());
        } finally {
            Files.deleteIfExists(tr.pdf());
        }
    }

    private static String docXml(Path docx) throws Exception {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(docx.toFile())) {
            return new String(z.getInputStream(z.getEntry("word/document.xml")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static int countRows(String xml) {
        return xml.split("<w:tr[ >]", -1).length - 1;
    }

    private static String rowOf(String xml, String text) {
        int p = xml.indexOf(text);
        assertTrue(p >= 0, "未找到文本:" + text);
        return xml.substring(xml.lastIndexOf("<w:tr ", p), xml.indexOf("</w:tr>", p) + 7);
    }

    private static String colWidths(String rowXml) {
        return java.util.regex.Pattern.compile("<w:tcW [^>]*/>")
                .matcher(rowXml).results().map(java.util.regex.MatchResult::group)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** 复制 hx_change.docx 并破坏 document.xml(删首个表格闭合标签) */
    private Path buildBrokenTemplate() throws Exception {
        File src = new File("../templates-src/hx_change.docx");
        Path out = Files.createTempFile("broken-", ".docx");
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(src);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(out))) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = en.nextElement();
                zos.putNextEntry(new java.util.zip.ZipEntry(e.getName()));
                if ("word/document.xml".equals(e.getName())) {
                    String xml = new String(zip.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    xml = xml.replaceFirst("</w:tbl>", ""); // 结构损坏,占位符仍在
                    zos.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    zip.getInputStream(e).transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return out;
    }

    private static long countTempResidue() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        File[] files = dir.listFiles((d, n) -> n.startsWith("render-"));
        return files == null ? 0 : files.length;
    }
}
