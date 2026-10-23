package com.fz.scitech.hx;

/** 流程单状态机(spec §3.2)与动作枚举(rp_hx_flow_record.action) */
public final class HxFlowStatus {
    private HxFlowStatus() {}

    public static final int DRAFT = 0;
    public static final int SUBMITTED = 1;  // 科技处预审中(草稿对科技处不可见)
    public static final int SCANNED = 2;    // 已回扫待审核
    public static final int APPROVED = 3;   // 通过(N4 写)
    public static final int REJECTED = 4;   // 退回(N4 写)
    /** 预审通过待盖章(2026-10-23 四轮反馈:预审通过教师方可上传盖章扫描件) */
    public static final int PRECHECK_PASSED = 5;

    public static final String ACT_SUBMIT = "submit";
    public static final String ACT_WITHDRAW = "withdraw";
    public static final String ACT_UPLOAD_SCAN = "upload_scan";
    public static final String ACT_RESUBMIT = "resubmit";
    /** N4 科技处审批动作(spec §3.2/§3.3;precheck_reject=盖章前预检退回) */
    public static final String ACT_APPROVE = "approve";
    /** 科技处预审通过(1→5,教师端收到通知后方可盖章回扫) */
    public static final String ACT_PRECHECK = "precheck";
    public static final String ACT_VERIFY = "verify";
    public static final String ACT_REJECT = "reject";
    public static final String ACT_PRECHECK_REJECT = "precheck_reject";
    /** HTML转正(2026-10-21 决策③):admin 审批前代改教师填报数据 */
    public static final String ACT_ADMIN_EDIT = "admin_edit";
}
