package com.fz.scitech.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysConfig;
import com.fz.scitech.system.mapper.SysConfigMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置只读接口(登录即可读;仅值型展示配置,敏感配置一律走环境变量不进此表)。
 * 已知键:deliver_pdf_visible——待签文件 PDF 入口显隐(2026-10-07 直出 Word 决策,
 * false=页面仅显示 Word 交付;双出落盘不受开关影响)。
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    private final SysConfigMapper configMapper;

    public ConfigController(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @GetMapping("/{key}")
    public Result<String> get(@PathVariable String key) {
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key).last("limit 1"));
        return Result.ok(c == null ? null : c.getConfigValue());
    }
}
