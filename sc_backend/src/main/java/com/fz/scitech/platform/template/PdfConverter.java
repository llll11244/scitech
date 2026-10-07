package com.fz.scitech.platform.template;

import com.fz.scitech.common.BizException;
import org.jodconverter.local.LocalConverter;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * docx → PDF 转换器(JODConverter Local 进程池)。
 * 转换失败抛业务异常(不落半成品,决策#34-③);组件缺失时明确提示。
 */
@Component
public class PdfConverter {

    private final OfficeManagerConfig officeManagerConfig;

    public PdfConverter(OfficeManagerConfig officeManagerConfig) {
        this.officeManagerConfig = officeManagerConfig;
    }

    /**
     * @param docxIn  源 docx 文件
     * @param pdfOut 目标 pdf 文件(调用方保证父目录存在)
     * 失败自动重试一次(决策#34-③:进程池自愈,偶发 Task cancelled 类抖动二过一)
     */
    public void toPdf(File docxIn, File pdfOut) {
        if (!officeManagerConfig.isAvailable()) {
            throw new BizException("PDF 组件缺失(未配置 LibreOffice),请联系管理员");
        }
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                LocalConverter.make(officeManagerConfig.getManager())
                        .convert(docxIn)
                        .to(pdfOut)
                        .execute();
                return;
            } catch (Exception e) {
                last = e;
                if (attempt == 1) {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BizException("PDF 转换中断");
                    }
                }
            }
        }
        throw new BizException("PDF 转换失败:" + (last == null ? "未知原因" : last.getMessage()));
    }
}
