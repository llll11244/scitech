package com.fz.scitech.hx.dto;

/**
 * 退回请求体(Task24):opinion 必填;isPrecheck=true=盖章前预检退回
 * (决策#18,status=1 即可退,留痕 action=precheck_reject,意见「盖章前预检」前缀由前端拼)。
 */
public record RejectBody(String opinion, Boolean isPrecheck) {
}
