package com.fz.scitech.platform.template;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 转换手工冒烟(决策:需 LibreOffice 就位,显式触发):
 * mvn test -Dtest=PdfSmokeTest -Dpdf.smoke=true
 * 验证:poi 生成中文 docx → JODConverter 转 PDF → 文件存在且非空。
 */
@EnabledIfSystemProperty(named = "pdf.smoke", matches = "true")
class PdfSmokeTest {

    @Test
    void docxToPdfChinese() throws Exception {
        File officeHome = new File("../libreoffice");
        assertTrue(new File(officeHome, "program/soffice.exe").exists()
                        || new File(officeHome, "program/soffice").exists(),
                "LibreOffice 未解包到 ../libreoffice");

        File docx = File.createTempFile("smoke", ".docx");
        File pdf = File.createTempFile("smoke", ".pdf");
        try {
            try (XWPFDocument doc = new XWPFDocument();
                 FileOutputStream out = new FileOutputStream(docx)) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun run = p.createRun();
                run.setFontFamily("宋体");
                run.setFontSize(14);
                run.setText("横向项目登记表 PDF 转换冒烟测试:中文渲染验证 12345");
                doc.write(out);
            }

            LocalOfficeManager manager = LocalOfficeManager.builder()
                    .officeHome(officeHome)
                    .portNumbers(2002)
                    .processTimeout(30_000L)
                    .taskExecutionTimeout(60_000L)
                    .build();
            try {
                manager.start();
                LocalConverter.make(manager).convert(docx).to(pdf).execute();
            } finally {
                try {
                    manager.stop();
                } catch (Exception ignored) {
                }
            }

            assertTrue(pdf.exists() && pdf.length() > 0, "PDF 未生成或为空");
            System.out.println("PDF 冒烟通过: " + pdf.getAbsolutePath() + " (" + pdf.length() + " bytes)");
        } finally {
            docx.delete();
            pdf.delete();
        }
    }
}
