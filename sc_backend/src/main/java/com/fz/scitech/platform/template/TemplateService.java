package com.fz.scitech.platform.template;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepoove.poi.XWPFTemplate;
import com.fz.scitech.common.BizException;
import com.fz.scitech.platform.template.entity.SysTemplate;
import com.fz.scitech.platform.template.mapper.SysTemplateMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 模板管理(决策#5):上传校验链 = ①.docx ②占位符完整性(双向比对,缺哪个报哪个)
 * ③内置静态样例试渲染(决策#34-③) ④页数≤2(除 hx_report)。
 * 通过后 version=max+1 启用,旧启用版转历史;已归档 PDF 不受影响。
 */
@Slf4j
@Service
public class TemplateService {

    private static final Pattern TAG = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Pattern TAG_WITH_BLOCK = Pattern.compile("\\{\\{\\??(/?)(\\w+)\\}\\}");

    private final SysTemplateMapper templateMapper;
    private final PdfConverter pdfConverter;
    private final String uploadDir;

    public TemplateService(SysTemplateMapper templateMapper, PdfConverter pdfConverter,
                           @Value("${sc.upload-dir}") String uploadDir) {
        this.templateMapper = templateMapper;
        this.pdfConverter = pdfConverter;
        this.uploadDir = uploadDir;
    }

    /** 上传新版模板:校验链全过→落库启用;返回落库记录(含预览 PDF 路径由 preview 接口出) */
    @Transactional
    public SysTemplate upload(MultipartFile file, String tplType, String remark) {
        TemplatePlaceholders.forType(tplType); // 校验 tplType 合法
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".docx")) {
            throw new BizException("模板必须是 .docx 文件");
        }

        Path tmp = null;
        try {
            tmp = Files.createTempFile("tpl-upload-", ".docx");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }

            // ② 占位符完整性(双向)
            Set<String> required = TemplatePlaceholders.forType(tplType);
            Set<String> actual = extractTags(tmp.toFile());
            List<String> missing = required.stream().filter(k -> !actual.contains(k)).sorted().collect(Collectors.toList());
            List<String> unknown = actual.stream().filter(k -> !required.contains(k)).sorted().collect(Collectors.toList());
            if (!missing.isEmpty()) {
                throw new BizException("模板缺少必备占位符:{{" + String.join("}} {{", missing) + "}},请对照《占位符清单》补齐");
            }
            if (!unknown.isEmpty()) {
                throw new BizException("模板含未知占位符:{{" + String.join("}} {{", unknown) + "}},请检查拼写(渲染将失败)");
            }

            // ③④ 样例试渲染 + 页数
            int pages = renderAndCountPages(tplType, tmp.toFile());
            int max = TemplatePlaceholders.maxPages(tplType);
            if (pages > max) {
                throw new BizException("样例渲染 " + pages + " 页,超过上限 " + max + " 页,请压缩模板版式");
            }

            // 落盘 + 版本落库(事务:旧启用版先转历史)
            Integer maxVer = templateMapper.selectList(Wrappers.<SysTemplate>lambdaQuery()
                            .eq(SysTemplate::getTplType, tplType)
                            .orderByDesc(SysTemplate::getVersion)
                            .last("LIMIT 1"))
                    .stream().findFirst().map(SysTemplate::getVersion).orElse(0);
            int newVer = maxVer + 1;

            File dir = new File(uploadDir, "templates/" + tplType);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BizException("模板目录创建失败:" + dir);
            }
            File dest = new File(dir, "v" + newVer + ".docx");
            Files.copy(tmp, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 旧启用版转历史
            SysTemplate enabling = templateMapper.selectOne(Wrappers.<SysTemplate>lambdaQuery()
                    .eq(SysTemplate::getTplType, tplType).eq(SysTemplate::getStatus, 1));
            if (enabling != null) {
                enabling.setStatus(0);
                templateMapper.updateById(enabling);
            }

            SysTemplate t = new SysTemplate();
            t.setTplType(tplType);
            t.setVersion(newVer);
            t.setFilePath("templates/" + tplType + "/v" + newVer + ".docx");
            t.setPlaceholderCheck(toJson(required));
            t.setStatus(1);
            t.setRemark(remark);
            templateMapper.insert(t);
            log.info("模板上传启用:{}/v{}(样例渲染 {} 页)", tplType, newVer, pages);
            return t;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("模板上传失败", e);
            throw new BizException("模板上传失败:" + e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 版本历史(启用版在前) */
    public List<SysTemplate> list(String tplType) {
        return templateMapper.selectList(Wrappers.<SysTemplate>lambdaQuery()
                .eq(tplType != null && !tplType.isEmpty(), SysTemplate::getTplType, tplType)
                .orderByAsc(SysTemplate::getTplType)
                .orderByDesc(SysTemplate::getVersion));
    }

    public SysTemplate getById(Long id) {
        return templateMapper.selectById(id);
    }

    /** 当前启用版 */
    public SysTemplate enabled(String tplType) {
        return templateMapper.selectOne(Wrappers.<SysTemplate>lambdaQuery()
                .eq(SysTemplate::getTplType, tplType).eq(SysTemplate::getStatus, 1));
    }

    public File templateFile(SysTemplate t) {
        File f = new File(uploadDir, t.getFilePath());
        if (!f.exists()) {
            throw new BizException("模板文件缺失:" + t.getFilePath());
        }
        return f;
    }

    /** 样例数据试渲染,返回 PDF 字节(预览接口用)——当前启用版 */
    public byte[] renderSamplePdf(String tplType) {
        SysTemplate t = enabled(tplType);
        if (t == null) {
            throw new BizException("该类型暂无启用版模板,请先上传");
        }
        return renderSamplePdfOf(t);
    }

    /** 指定版本的样例渲染 */
    public byte[] renderSamplePdfOf(SysTemplate t) {
        Path tmpDocx = null;
        Path tmpPdf = null;
        try {
            tmpDocx = Files.createTempFile("tpl-preview-", ".docx");
            tmpPdf = Files.createTempFile("tpl-preview-", ".pdf");
            renderDocx(t.getTplType(), templateFile(t), tmpDocx.toFile());
            pdfConverter.toPdf(tmpDocx.toFile(), tmpPdf.toFile());
            return Files.readAllBytes(tmpPdf);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("样例渲染失败:" + e.getMessage());
        } finally {
            cleanup(tmpDocx);
            cleanup(tmpPdf);
        }
    }

    /** poi-tl 渲染到目标 docx(样例数据) */
    public void renderDocx(String tplType, File templateFile, File outDocx) throws Exception {
        renderDocx(tplType, templateFile, outDocx, SampleData.forType(tplType));
    }

    /** poi-tl 渲染到目标 docx(显式数据模型;N3 RenderModelBuilder 组装后走此口径) */
    public void renderDocx(String tplType, File templateFile, File outDocx, Map<String, Object> data) throws Exception {
        try (FileInputStream in = new FileInputStream(templateFile);
             java.io.FileOutputStream out = new java.io.FileOutputStream(outDocx)) {
            XWPFTemplate template = XWPFTemplate.compile(in).render(data);
            template.writeAndClose(out);
        }
    }

    /** 样例数据 docx 直出字节(预览接口用,不转 PDF)——当前启用版 */
    public byte[] renderSampleDocx(String tplType) {
        SysTemplate t = enabled(tplType);
        if (t == null) {
            throw new BizException("该类型暂无启用版模板,请先上传");
        }
        Path tmpDocx = null;
        try {
            tmpDocx = Files.createTempFile("tpl-preview-", ".docx");
            renderDocx(t.getTplType(), templateFile(t), tmpDocx.toFile());
            return Files.readAllBytes(tmpDocx);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("样例渲染失败:" + e.getMessage());
        } finally {
            cleanup(tmpDocx);
        }
    }

    /** 渲染并统计 PDF 页数(校验链 ③④ 共用) */
    public int renderAndCountPages(String tplType, File templateFile) throws Exception {
        Path tmpDocx = null;
        Path tmpPdf = null;
        try {
            tmpDocx = Files.createTempFile("tpl-check-", ".docx");
            tmpPdf = Files.createTempFile("tpl-check-", ".pdf");
            renderDocx(tplType, templateFile, tmpDocx.toFile());
            pdfConverter.toPdf(tmpDocx.toFile(), tmpPdf.toFile());
            try (PDDocument doc = PDDocument.load(tmpPdf.toFile())) {
                return doc.getNumberOfPages();
            }
        } finally {
            cleanup(tmpDocx);
            cleanup(tmpPdf);
        }
    }

    /** 提取 docx 全部标签(含区块对 {?x}} {{/x}} 的 x 与内层 {{this}}) */
    private Set<String> extractTags(File docx) throws Exception {
        Set<String> tags = new LinkedHashSet<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(docx)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
            Pattern any = Pattern.compile("\\{\\{[^}]*\\}\\}");
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = en.nextElement();
                if (!e.getName().endsWith(".xml") || !e.getName().startsWith("word/")) {
                    continue;
                }
                String xml = new String(zip.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                Matcher m = any.matcher(xml);
                while (m.find()) {
                    String raw = m.group();
                    Matcher inner = TAG.matcher(raw);
                    while (inner.find()) {
                        tags.add(inner.group(1));
                    }
                    Matcher blk = TAG_WITH_BLOCK.matcher(raw);
                    while (blk.find()) {
                        tags.add(blk.group(2));
                    }
                }
            }
        }
        return tags;
    }

    private String toJson(Set<String> tags) {
        return "[" + tags.stream().sorted().map(t -> "\"" + t + "\"").collect(Collectors.joining(",")) + "]";
    }

    private void cleanup(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {
            }
        }
    }
}
