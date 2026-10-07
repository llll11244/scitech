package com.fz.scitech.hx.dto;

import java.math.BigDecimal;

/**
 * 审批通过请求体(Task24):验收流程 verifiedAmount(核定到账金额,元)必填,
 * 登记/变更流程无字段(传空对象即可)。
 */
public record ReviewBody(BigDecimal verifiedAmount) {
}
