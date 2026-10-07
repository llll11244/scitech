package com.fz.scitech.platform.template;

import com.deepoove.poi.XWPFTemplate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板试渲染冒烟(决策#5 §5.4;2026-10-07 保真直出后页数上限=TemplatePlaceholders.maxPages):
 * mvn test -Dtest=RenderSmokeTest -Drender.smoke=true
 * poi-tl 渲染样例数据 → LibreOffice 转 PDF → PDFBox 页数断言;
 * 产物留 target/render-smoke/ 供人工对照原表版式。
 */
@EnabledIfSystemProperty(named = "render.smoke", matches = "true")
class RenderSmokeTest {

    private static final File TPL_DIR = new File("../templates-src");
    private static final File OUT_DIR = new File("target/render-smoke");
    private static LocalOfficeManager manager;

    @BeforeAll
    static void setUp() throws Exception {
        assertTrue(TPL_DIR.isDirectory(), "templates-src 不存在");
        OUT_DIR.mkdirs();
        manager = LocalOfficeManager.builder()
                .officeHome(new File("../libreoffice"))
                .portNumbers(2002)
                .processTimeout(30_000L)
                .taskExecutionTimeout(60_000L)
                .build();
        manager.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (manager != null) {
            try {
                manager.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void renderAndCheck(String tplType, int maxPages) throws Exception {
        Map<String, Object> data = SampleData.forType(tplType);

        File tplFile = new File(TPL_DIR, tplType + ".docx");
        File docxOut = new File(OUT_DIR, tplType + "_sample.docx");
        File pdfOut = new File(OUT_DIR, tplType + "_sample.pdf");
        try (FileInputStream in = new FileInputStream(tplFile)) {
            XWPFTemplate template = XWPFTemplate.compile(in).render(data);
            try (FileOutputStream out = new FileOutputStream(docxOut)) {
                template.writeAndClose(out);
            }
        }
        LocalConverter.make(manager).convert(docxOut).to(pdfOut).execute();

        int pages;
        try (PDDocument doc = PDDocument.load(pdfOut)) {
            pages = doc.getNumberOfPages();
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setPageEnd("\f");
            String full = stripper.getText(doc);
            String[] perPage = full.split("\f");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new File(OUT_DIR, tplType + "_pages.txt"), "UTF-8")) {
                for (int i = 0; i < perPage.length; i++) {
                    pw.println("===== 第" + (i + 1) + "页 =====");
                    pw.println(perPage[i].replaceAll("\\s+", " "));
                }
            }
        }
        System.out.printf("%s: docx=%s pdf=%s pages=%d%n", tplType, docxOut.length(), pdfOut.length(), pages);
        assertTrue(pages >= 1, tplType + " 渲染页数异常:" + pages);
        assertTrue(pages <= maxPages, tplType + " 页数超限:" + pages + " > " + maxPages);
    }

    @Test
    void registerWithinPageLimit() throws Exception {
        renderAndCheck("hx_register", TemplatePlaceholders.maxPages("hx_register"));
    }

    @Test
    void changeWithinPageLimit() throws Exception {
        renderAndCheck("hx_change", TemplatePlaceholders.maxPages("hx_change"));
    }

    @Test
    void acceptanceWithinPageLimit() throws Exception {
        renderAndCheck("hx_acceptance", TemplatePlaceholders.maxPages("hx_acceptance"));
    }

    @Test
    void reportAtLeastOnePage() throws Exception {
        renderAndCheck("hx_report", TemplatePlaceholders.maxPages("hx_report"));
    }
}
