package com.fz.scitech.platform.backup;

import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份管理接口(仅 admin,spec §八):
 * status=启动自检(available=false 前端按钮置灰提示)/list=列表/run=手动异步(进行中语义)
 * /download=下载(zip)/DELETE=物理删。backup 目录不在附件鉴权范围,独立于此。
 */
@RestController
@RequestMapping("/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /** 状态:available/message/cron/keep/running(自检结果+当前是否执行中) */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(backupService.status());
    }

    /** 备份列表(新→旧):[{name,size,time}] */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(backupService.list());
    }

    /** 手动备份(异步立即返回):started=true 已开始 / false 已在进行中 */
    @PostMapping("/run")
    public Result<Map<String, Object>> run(HttpServletRequest request) {
        requireAdmin(request);
        boolean started = backupService.triggerManual();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("started", started);
        data.put("message", started ? "备份已开始,请稍后刷新列表查看结果" : "已有备份进行中,请稍后刷新列表查看结果");
        return Result.ok(data);
    }

    /** 下载备份 zip(name 白名单+canonical 校验在 service,防路径遍历) */
    @GetMapping("/download")
    public void download(@RequestParam String name, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        requireAdmin(request);
        File f = backupService.backupFile(name);
        if (!f.isFile()) {
            throw new BizException("备份文件不存在");
        }
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(f.getName(), StandardCharsets.UTF_8));
        response.setContentLengthLong(f.length());
        new FileSystemResource(f).getInputStream().transferTo(response.getOutputStream());
    }

    /** 删除备份(物理删) */
    @DeleteMapping
    public Result<Void> delete(@RequestParam String name, HttpServletRequest request) {
        requireAdmin(request);
        backupService.delete(name);
        return Result.ok();
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            throw new BizException("仅管理员可操作");
        }
    }
}
