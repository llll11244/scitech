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
 * → PdfConverter 转 PDF → 页数校验(≤2,报告类除外) → PDF 元数据(title=流程单号,author=操作人)
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
     * 正式渲染并落盘登记(待签 hx_pdf / 终版 hx_final),返回附件 id。
     * 旧版本 PDF 由调用方标记作废(biz_type 语义:hx_pdf 含作废版本,保留文件不入列表)。
     */
    public Long render(RenderContext ctx, java.util.Map<String, Object> model) {
        SysTemplate tpl = templateService.enabled(ctx.tplType());
        if (tpl == null) {
            throw new BizException("模板类型 " + ctx.tplType() + " 暂无启用版,请联系管理员上传");
        }
        Path tmp = null;
        File dest = null;
        try {
            tmp = renderToTemp(ctx.tplType(), templateService.templateFile(tpl), model,
                    ctx.flowType() + "-" + ctx.flowId(), ctx.operatorName()).pdf();

            // 落盘 upload/hx/{projectId}/{flowType}/{flowId}/
            File dir = new File(uploadDir, "hx/" + ctx.projectId() + "/" + ctx.flowType() + "/" + ctx.flowId());
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BizException("渲染目录创建失败:" + dir);
            }
            String prefix = ctx.finalVersion() ? "final_" : "pending_";
            dest = new File(dir, prefix + LocalDateTime.now().format(TS) + ".pdf");
            Files.copy(tmp, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 附件登记(失败则删除已落盘文件,不留无主产物)
            RpAttachment att = new RpAttachment();
            att.setProjectId(ctx.projectId());
            att.setBizType(ctx.finalVersion() ? "hx_final" : "hx_pdf");
            att.setFileName(dest.getName());
            att.setFilePath(toRelative(dest));
            att.setFileSize(dest.length());
            att.setUploaderId(ctx.operatorId());
            attachmentMapper.insert(att);
            log.info("PDF渲染落盘:{}/{} flow={}{} pages={} bytes={}", ctx.projectId(), ctx.flowType(),
                    ctx.flowId(), ctx.finalVersion() ? "(终版)" : "", countPages(dest.toPath()), dest.length());
            return att.getId();
        } catch (BizException e) {
            cleanupQuietly(dest);
            throw e;
        } catch (Exception e) {
            log.error("PDF渲染失败 flowId={}", ctx.flowId(), e);
            cleanupQuietly(dest);
            throw new BizException("PDF 生成失败:" + e.getMessage());
        } finally {
            cleanupQuietly(tmp == null ? null : tmp.toFile());
        }
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
        Path tmpPdf = null;
        Path metaPdf = null;
        try {
            tmpDocx = Files.createTempFile("render-", ".docx");
            tmpPdf = Files.createTempFile("render-", ".pdf");
            templateService.renderDocx(tplType, templateFile, tmpDocx.toFile());
            pdfConverter.toPdf(tmpDocx.toFile(), tmpPdf.toFile());

            int pages = countPages(tmpPdf);
            int max = TemplatePlaceholders.maxPages(tplType);
            if (pages > max) {
                throw new BizException("渲染结果 " + pages + " 页,超过上限 " + max + " 页(模板版式或内容超长)");
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
            cleanupQuietly(tmpDocx == null ? null : tmpDocx.toFile());
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
