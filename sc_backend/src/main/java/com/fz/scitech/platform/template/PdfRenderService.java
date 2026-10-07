package com.fz.scitech.platform.template;

import com.fz.scitech.common.BizException;
import com.fz.scitech.platform.attachment.RpAttachment;
import com.fz.scitech.platform.attachment.mapper.RpAttachmentMapper;
import com.fz.scitech.platform.template.dto.RenderContext;
import com.fz.scitech.platform.template.entity.SysTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF 渲染管线(决策#4/#34-③/#36):
 * model(业务模块组装,含勾选组/大写金额/万元换算) → poi-tl 填当前启用版模板(临时 docx)
 * → PdfConverter 转 PDF → 页数校验(≤TemplatePlaceholders.maxPages,保真直出后登记/验收 3 页) → PDF 元数据(title=流程单号,author=操作人)
 * → 落盘 upload/hx/{projectId}/{flowType}/{flowId}/ → rp_attachment 登记。
 * 失败抛异常不落半成品:一切中间产物(含已落盘未登记的 PDF)清理后才抛。
 */
@Slf4j
@Service
public class PdfRenderService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final TemplateService templateService;
    private final PdfConverter pdfConverter;
    private final RpAttachmentMapper attachmentMapper;
    private final String uploadDir;

    public PdfRenderService(TemplateService templateService, PdfConverter pdfConverter,
                            RpAttachmentMapper attachmentMapper,
                            @Value("${sc.upload-dir}") String uploadDir) {
        this.templateService = templateService;
        this.pdfConverter = pdfConverter;
        this.attachmentMapper = attachmentMapper;
        this.uploadDir = uploadDir;
    }

    /** 预览结果(决策#36:分屏实时预览用,不落盘;pages 供前端显示页数约束) */
    public record PreviewResult(byte[] pdf, int pages) {
    }

    /** 内部管线产物 */
    record TempRender(Path pdf, int pages) {
    }

    /**
     * 正式渲染双出落盘登记(2026-10-07"直出 Word"决策):
     * docx=主交付(老师用 Word 打印,版式与模板零差异) + PDF=副产物隐藏留存
     * (LibreOffice 口径,前端显隐由 sys_config.deliver_pdf_visible 开关控制,双出不受开关影响)。
     * 返回 docx 附件 id。旧版本由调用方标记作废(biz_type 含作废版本,保留文件不入列表)。
     */
    public Long render(RenderContext ctx, java.util.Map<String, Object> model) {
        SysTemplate tpl = templateService.enabled(ctx.tplType());
        if (tpl == null) {
            throw new BizException("模板类型 " + ctx.tplType() + " 暂无启用版,请联系管理员上传");
        }
        Path tmpDocx = null;
        File docxDest = null;
        File pdfDest = null;
        try {
            tmpDocx = Files.createTempFile("render-", ".docx");
            templateService.renderDocx(ctx.tplType(), templateService.templateFile(tpl), tmpDocx.toFile(), model);
            TempRender r = pdfFromDocx(tmpDocx, TemplatePlaceholders.maxPages(ctx.tplType()),
                    ctx.flowType() + "-" + ctx.flowId(), ctx.operatorName());
            try {
                // 双出落盘 upload/hx/{projectId}/{flowType}/{flowId}/
                File dir = new File(uploadDir, "hx/" + ctx.projectId() + "/" + ctx.flowType() + "/" + ctx.flowId());
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new BizException("渲染目录创建失败:" + dir);
                }
                String prefix = ctx.finalVersion() ? "final_" : "pending_";
                String ts = LocalDateTime.now().format(TS);
                // 文件名带 tplType 段:验收单同秒渲两张(hx_acceptance/hx_report)时防 REPLACE_EXISTING 互相覆盖
                docxDest = new File(dir, prefix + ts + "_" + ctx.tplType() + ".docx");
                Files.copy(tmpDocx, docxDest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                pdfDest = new File(dir, prefix + ts + "_" + ctx.tplType() + ".pdf");
                Files.copy(r.pdf(), pdfDest.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // 附件登记(失败则删除已落盘文件,不留无主产物)
                Long docxId = insertAttachment(ctx, docxDest, stem(ctx, "docx"));
                insertAttachment(ctx, pdfDest, stem(ctx, "pdf"));
                log.info("渲染双出:{}/{} flow={}{} pages={} docx={} pdf={}", ctx.projectId(), ctx.flowType(),
                        ctx.flowId(), ctx.finalVersion() ? "(终版)" : "", r.pages(), docxDest.length(), pdfDest.length());
                return docxId;
            } finally {
                cleanupQuietly(r.pdf().toFile());
            }
        } catch (BizException e) {
            cleanupQuietly(docxDest);
            cleanupQuietly(pdfDest);
            throw e;
        } catch (Exception e) {
            log.error("渲染失败 flowId={}", ctx.flowId(), e);
            cleanupQuietly(docxDest);
            cleanupQuietly(pdfDest);
            throw new BizException("渲染失败:" + e.getMessage());
        } finally {
            cleanupQuietly(tmpDocx == null ? null : tmpDocx.toFile());
        }
    }

    /** docx 直出预览流(N3 填报分屏预览,前端 docx-preview 渲染):纯 poi-tl 填充,不转换不落盘 */
    public byte[] renderDocxStream(String tplType, java.util.Map<String, Object> model) {
        SysTemplate tpl = templateService.enabled(tplType);
        if (tpl == null) {
            throw new BizException("模板类型 " + tplType + " 暂无启用版,请联系管理员上传");
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("render-", ".docx");
            templateService.renderDocx(tplType, templateService.templateFile(tpl), tmp.toFile(), model);
            return Files.readAllBytes(tmp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("docx 渲染失败 {}", tplType, e);
            throw new BizException("docx 渲染失败:" + e.getMessage());
        } finally {
            cleanupQuietly(tmp == null ? null : tmp.toFile());
        }
    }

    private Long insertAttachment(RenderContext ctx, File f, String bizType) {
        RpAttachment att = new RpAttachment();
        att.setProjectId(ctx.projectId());
        att.setBizType(bizType);
        att.setFileName(f.getName());
        att.setFilePath(toRelative(f));
        att.setFileSize(f.length());
        att.setUploaderId(ctx.operatorId());
        attachmentMapper.insert(att);
        return att.getId();
    }

    /**
     * 附件 biz_type 按 tplType 推导(Task21 验收双单据):hx_report→hx_report_docx/hx_report_pdf
     * (验收详情/下载按此区分两张),其余流程维持 hx_docx/hx_pdf。
     * 终版(N4 验收确认)同样分 tplType:hx_final_docx 与 hx_final_report_docx——
     * 终版前缀与报告段叠加,否则验收两张终版互混成同一 biz_type(N3 终审注意②)。
     */
    private static String stem(RenderContext ctx, String ext) {
        String prefix = ctx.finalVersion() ? "hx_final" : "hx";
        String reportSeg = "hx_report".equals(ctx.tplType()) ? "_report" : "";
        return prefix + reportSeg + "_" + ext;
    }

    /** 预览渲染(决策#36):字节流返回,不落盘不登记;页数超限不抛错(预览如实反映,由前端提示) */
    public PreviewResult renderPreview(String tplType, java.util.Map<String, Object> model) {
        SysTemplate tpl = templateService.enabled(tplType);
        if (tpl == null) {
            throw new BizException("模板类型 " + tplType + " 暂无启用版,请联系管理员上传");
        }
        Path tmp = null;
        try {
            TempRender r = renderToTemp(tplType, templateService.templateFile(tpl), model,
                    tplType + "-preview", "preview");
            tmp = r.pdf();
            return new PreviewResult(Files.readAllBytes(tmp), r.pages());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("预览渲染失败 {}", tplType, e);
            throw new BizException("预览渲染失败:" + e.getMessage());
        } finally {
            cleanupQuietly(tmp == null ? null : tmp.toFile());
        }
    }

    /**
     * 管线核心(包级开放供失败路径测试):渲染→转换→页数→元数据,全部在临时文件完成。
     * 页数超上限抛 BizException(正式渲染必须拦)。
     */
    TempRender renderToTemp(String tplType, File templateFile, java.util.Map<String, Object> model,
                            String title, String author) throws Exception {
        Path tmpDocx = null;
        try {
            tmpDocx = Files.createTempFile("render-", ".docx");
            templateService.renderDocx(tplType, templateFile, tmpDocx.toFile(), model);
            return pdfFromDocx(tmpDocx, TemplatePlaceholders.maxPages(tplType), title, author);
        } finally {
            cleanupQuietly(tmpDocx == null ? null : tmpDocx.toFile());
        }
    }

    /** 填充后的 docx→PDF(转换+页数校验+元数据),全在临时文件完成;页数超上限抛 BizException */
    private TempRender pdfFromDocx(Path tmpDocx, int maxPages, String title, String author) throws Exception {
        Path tmpPdf = null;
        Path metaPdf = null;
        try {
            tmpPdf = Files.createTempFile("render-", ".pdf");
            pdfConverter.toPdf(tmpDocx.toFile(), tmpPdf.toFile());

            int pages = countPages(tmpPdf);
            if (pages > maxPages) {
                throw new BizException("渲染结果 " + pages + " 页,超过上限 " + maxPages + " 页(模板版式或内容超长)");
            }
            // PDF 元数据:生成时间/流程单号/操作人(决策#10)
            metaPdf = Files.createTempFile("render-", ".pdf");
            try (PDDocument doc = PDDocument.load(tmpPdf.toFile())) {
                PDDocumentInformation info = doc.getDocumentInformation();
                info.setTitle(title);
                info.setAuthor(author);
                info.setCreator("科研管理平台");
                info.setModificationDate(java.util.Calendar.getInstance());
                doc.save(metaPdf.toFile());
            }
            return new TempRender(metaPdf, pages);
        } finally {
            cleanupQuietly(tmpPdf == null ? null : tmpPdf.toFile());
        }
    }

    private int countPages(Path pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            return doc.getNumberOfPages();
        }
    }

    private String toRelative(File f) {
        String p = f.getPath().replace('\\', '/');
        String root = new File(uploadDir).getPath().replace('\\', '/');
        if (p.startsWith(root + "/")) {
            return p.substring(root.length() + 1);
        }
        return p;
    }

    private void cleanupQuietly(File f) {
        if (f != null) {
            try {
                Files.deleteIfExists(f.toPath());
            } catch (Exception ignored) {
            }
        }
    }
}
