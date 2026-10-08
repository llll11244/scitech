package com.fz.scitech;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 科研管理平台启动类
 */
@SpringBootApplication
@MapperScan("com.fz.scitech.**.mapper")
@EnableScheduling // 备份子系统定时任务(Task27,sc.backup-cron)
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
