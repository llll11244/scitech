package com.fz.scitech.platform.template;

import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.platform.template.entity.SysTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 模板管理接口(仅 admin,决策#5/技术约束#9)
 */
@RestController
@RequestMapping("/template")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /** 上传新版模板(校验链:docx/占位符双向比对/样例试渲染/页数) */
    @PostMapping("/upload")
    public Result<SysTemplate> upload(@RequestParam MultipartFile file,
                                      @RequestParam String tplType,
                                      @RequestParam(required = false) String remark,
                                      HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(templateService.upload(file, tplType, remark));
    }

    /** 版本历史(启用版在前);tplType 可选 */
    @GetMapping("/list")
    public Result<List<SysTemplate>> list(@RequestParam(required = false) String tplType) {
        return Result.ok(templateService.list(tplType));
    }

    /** 下载模板 docx */
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysTemplate t = templateService.getById(id);
        if (t == null) {
            throw new BizException("模板不存在");
        }
        File f = templateService.templateFile(t);
        writeDocx(response, f, t.getTplType() + "_v" + t.getVersion() + ".docx");
    }

    /** 当前启用版(或指定版)样例数据渲染预览 PDF(inline 展示) */
    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysTemplate t = templateService.getById(id);
        if (t == null) {
            throw new BizException("模板不存在");
        }
        byte[] pdf = renderPdfByVersion(t);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setContentLength(pdf.length);
        response.setHeader("Content-Disposition", "inline; filename=" + t.getTplType() + "_preview.pdf");
        response.getOutputStream().write(pdf);
    }

    /** 当前启用版样例预览(无需 id) */
    @GetMapping("/preview")
    public void previewEnabled(@RequestParam String tplType, HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        requireAdmin(request);
        byte[] pdf = templateService.renderSamplePdf(tplType);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setContentLength(pdf.length);
        response.setHeader("Content-Disposition", "inline; filename=" + tplType + "_preview.pdf");
        response.getOutputStream().write(pdf);
    }

    private byte[] renderPdfByVersion(SysTemplate t) {
        return templateService.renderSamplePdfOf(t);
    }

    private void writeDocx(HttpServletResponse response, File f, String name) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(name, StandardCharsets.UTF_8));
        response.setContentLengthLong(f.length());
        new FileSystemResource(f).getInputStream().transferTo(response.getOutputStream());
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            throw new BizException("仅管理员可操作");
        }
    }
}
