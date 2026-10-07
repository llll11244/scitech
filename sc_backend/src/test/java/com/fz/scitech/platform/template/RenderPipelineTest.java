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
        PdfConverter converter = new PdfConverter(cfg);
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
