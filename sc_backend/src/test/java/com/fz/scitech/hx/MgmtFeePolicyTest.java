package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fz.scitech.system.entity.SysConfig;
import com.fz.scitech.system.mapper.SysConfigMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理费比例策略口径:比例=科技处后台 sys_config(hx_mgmt_fee_rate,百分数),教师不可设;
 * 金额=合同经费×比例 自动换算(setScale(2) HALF_UP),收取关掉→null;
 * 配置缺失/非法兜底默认 2%(种子保证存在,防御式)。
 */
class MgmtFeePolicyTest {

    private final SysConfigMapper configMapper = mock(SysConfigMapper.class);
    private final MgmtFeePolicy policy = new MgmtFeePolicy(configMapper);

    private void givenConfig(String value) {
        SysConfig c = new SysConfig();
        c.setConfigKey(MgmtFeePolicy.CONFIG_KEY);
        c.setConfigValue(value);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(c);
    }

    @Test
    void configuredRateComputesAmount() {
        givenConfig("2");
        assertEquals(new BigDecimal("7100.00"), policy.compute(1, new BigDecimal("355000.00")));
    }

    @Test
    void chargeOffClearsAmount() {
        givenConfig("2");
        assertNull(policy.compute(0, new BigDecimal("355000.00")));
    }

    @Test
    void missingConfigFallsBackToDefaultRate() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertEquals(new BigDecimal("2000.00"), policy.compute(1, new BigDecimal("100000.00")));
    }

    @Test
    void illegalConfigFallsBackToDefaultRate() {
        givenConfig("abc");
        assertEquals(new BigDecimal("2000.00"), policy.compute(1, new BigDecimal("100000.00")));
    }

    @Test
    void roundsHalfUpAtTwoDecimals() {
        givenConfig("1.5");   // 33335 × 1.5% = 500.025 → 500.03
        assertEquals(new BigDecimal("500.03"), policy.compute(1, new BigDecimal("33335")));
    }

    @Test
    void nullContractAmountStaysNull() {
        givenConfig("2");
        assertNull(policy.compute(1, null));
        assertNull(policy.compute(null, new BigDecimal("355000.00")));
    }
}
