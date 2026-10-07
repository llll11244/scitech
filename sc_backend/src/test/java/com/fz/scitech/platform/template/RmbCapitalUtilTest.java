package com.fz.scitech.platform.template;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 人民币大写工具测试(计划 Task 9 用例+壹亿边界)
 */
class RmbCapitalUtilTest {

    @Test
    void standardCases() {
        assertEquals("叁拾伍万伍仟元整", RmbCapitalUtil.toCapital(new BigDecimal("355000.00")));
        assertEquals("壹拾伍万叁仟捌佰肆拾陆元贰角柒分", RmbCapitalUtil.toCapital(new BigDecimal("153846.27")));
        assertEquals("伍角", RmbCapitalUtil.toCapital(new BigDecimal("0.5")));
        assertEquals("伍分", RmbCapitalUtil.toCapital(new BigDecimal("0.05")));
        assertEquals("壹佰元零伍分", RmbCapitalUtil.toCapital(new BigDecimal("100.05")));
        assertEquals("壹万零伍元整", RmbCapitalUtil.toCapital(new BigDecimal("10005.00")));
        assertEquals("壹拾元整", RmbCapitalUtil.toCapital(new BigDecimal("10")));
        assertEquals("零元整", RmbCapitalUtil.toCapital(BigDecimal.ZERO));
    }

    @Test
    void yiBoundary() {
        // 上限边界(壹亿以内最大值)
        assertEquals("玖仟玖佰玖拾玖万玖仟玖佰玖拾玖元玖角玖分",
                RmbCapitalUtil.toCapital(new BigDecimal("99999999.99")));
        // 恰壹亿 → 超上限抛异常(计划约定:上限 9999万9999.99)
        assertThrows(IllegalArgumentException.class,
                () -> RmbCapitalUtil.toCapital(new BigDecimal("100000000")));
        // 千万级在限内,正常输出
        assertEquals("壹仟万元整", RmbCapitalUtil.toCapital(new BigDecimal("10000000")));
    }

    @Test
    void rejectsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> RmbCapitalUtil.toCapital(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> RmbCapitalUtil.toCapital(null));
    }
}
