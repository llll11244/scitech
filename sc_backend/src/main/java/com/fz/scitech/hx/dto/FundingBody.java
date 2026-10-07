package com.fz.scitech.hx.dto;

import java.math.BigDecimal;

/**
 * 到账录入请求体(Task25,spec §4.8):amount(元)必填且大于 0;receivedDate 必填 yyyy-MM-dd;
 * payer 默认建议=委托单位名(前端预填,后端不强制);bankFlowNo/remark 选填。
 */
public record FundingBody(Long projectId, BigDecimal amount, String receivedDate,
                          String payer, String bankFlowNo, String remark) {
}
