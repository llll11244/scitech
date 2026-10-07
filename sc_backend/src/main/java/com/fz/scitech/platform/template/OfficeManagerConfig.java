package com.fz.scitech.platform.template;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * LibreOffice 进程池配置(决策#4/#34-③)。
 * office-home 指向部署包内便携版目录(sc.office-home,默认 ./libreoffice);
 * 启动自检:目录/soffice 不存在 → log.warn 并降级(接口层报「PDF 组件缺失」),
 * 不阻断应用启动;进程池由 JODConverter 管理,崩溃自动重启。
 */
@Slf4j
@Configuration
public class OfficeManagerConfig {

    @Value("${sc.office-home}")
    private String officeHome;

    private LocalOfficeManager officeManager;
    private volatile boolean available = false;

    @PostConstruct
    public void init() {
        File home = new File(officeHome);
        // Windows=program/soffice.exe,Linux=program/soffice(换服务器替换目录即可)
        boolean found = new File(home, "program/soffice.exe").exists()
                || new File(home, "program/soffice").exists();
        if (!found) {
            log.warn("LibreOffice 未找到({}),PDF 功能降级:相关接口将报「PDF 组件缺失」", officeHome);
            return;
        }
        officeManager = LocalOfficeManager.builder()
                .officeHome(home)
                .portNumbers(2002)
                .processTimeout(30_000L)
                .taskExecutionTimeout(60_000L)
                .maxTasksPerProcess(200)
                .build();
        try {
            officeManager.start();
            available = true;
            log.info("LibreOffice 进程池已启动(office-home={}, port=2002)", officeHome);
        } catch (Exception e) {
            log.error("LibreOffice 进程池启动失败,PDF 功能降级:{}", e.getMessage());
            officeManager = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (officeManager != null) {
            try {
                officeManager.stop();
            } catch (Exception ignored) {
                // 退出阶段尽力而为
            }
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public OfficeManager getManager() {
        return officeManager;
    }
}
