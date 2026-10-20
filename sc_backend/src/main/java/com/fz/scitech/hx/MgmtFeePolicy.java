package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fz.scitech.system.entity.SysConfig;
import com.fz.scitech.system.mapper.SysConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 管理费比例策略:比例归科技处后台统一设定(sys_config.hx_mgmt_fee_rate,百分数,
 * 「系统参数」页维护),教师端不可设;金额=合同经费×比例 自动换算(setScale(2) HALF_UP)。
 * 口径:登记保存/草稿编辑时重算落库,提交即快照定格;变更通过改经费时按当时比例重算。
 * 配置缺失/非法兜底默认 2%(INSERT IGNORE 种子保证存在,防御式)并告警。
 */
@Slf4j
@Component
public class MgmtFeePolicy {

    static final String CONFIG_KEY = "hx_mgmt_fee_rate";
    static final BigDecimal DEFAULT_RATE = new BigDecimal("2");

    private final SysConfigMapper configMapper;

    public MgmtFeePolicy(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /** 当前配置比例(%);缺配置/非法值兜底 2% */
    public BigDecimal ratePct() {
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, CONFIG_KEY).last("limit 1"));
        if (c != null && c.getConfigValue() != null) {
            try {
                return new BigDecimal(c.getConfigValue().trim());
            } catch (NumberFormatException e) {
                log.warn("管理费比例配置非法({}),按默认 2% 兜底", c.getConfigValue());
            }
        } else {
            log.warn("管理费比例配置缺失,按默认 2% 兜底");
        }
        return DEFAULT_RATE;
    }

    /** 换算管理费金额:收取=1 且经费非空 → 经费×比例% 两位小数(HALF_UP);否则 null */
    public BigDecimal compute(Integer chargeMgmtFee, BigDecimal contractAmount) {
        if (chargeMgmtFee == null || chargeMgmtFee != 1 || contractAmount == null) {
            return null;
        }
        return contractAmount.multiply(ratePct())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
