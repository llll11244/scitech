package com.fz.scitech.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysConfig;
import com.fz.scitech.system.mapper.SysConfigMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 系统配置接口。GET 登录即可读;仅值型展示配置,敏感配置一律走环境变量不进此表。
 * 已知键:deliver_pdf_visible——待签文件 PDF 入口显隐(2026-10-07 直出 Word 决策,
 * false=页面仅显示 Word 交付;双出落盘不受开关影响);
 * hx_mgmt_fee_rate——横向管理费比例(%),科技处「系统参数」页维护,教师端只读自动换算。
 * PUT 仅 admin 且仅白名单键(值校验随键;未列入白名单的键一律不可改)。
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    /**
     * 允许经 PUT 修改的键 → 合法值上限(比例类百分数)。
     * 键名与业务侧 hx.MgmtFeePolicy.CONFIG_KEY/data.sql 种子同串;平台层不 import 业务包(决策#32),
     * 漂移由 test_admin_pages.py 的 PUT 用例兜底。
     */
    private static final Map<String, BigDecimal> EDITABLE = Map.of("hx_mgmt_fee_rate", new BigDecimal("100"));

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

    /** 修改值型配置(admin):body={"value":"2"};比例键限 0~100 两位小数,更新失败(键不存在)即拒 */
    @PutMapping("/{key}")
    public Result<Void> put(@PathVariable String key, @RequestBody Map<String, String> body,
                            HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            throw new BizException("仅管理员可操作");
        }
        BigDecimal max = EDITABLE.get(key);
        if (max == null) {
            throw new BizException("该配置项不可修改");
        }
        String value = body == null ? null : body.get("value");
        BigDecimal v;
        try {
            v = new BigDecimal(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new BizException("配置值须为数字");
        }
        if (v.scale() > 2) {
            throw new BizException("最多两位小数");
        }
        if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(max) > 0) {
            throw new BizException("取值须在 0~" + max.stripTrailingZeros().toPlainString() + " 之间");
        }
        int n = configMapper.update(null, new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .set(SysConfig::getConfigValue, v.toPlainString()));
        if (n == 0) {
            throw new BizException("配置项不存在,请重启应用初始化");
        }
        return Result.ok(null);
    }
}
