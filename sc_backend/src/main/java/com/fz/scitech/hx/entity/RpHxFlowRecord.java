package com.fz.scitech.hx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 横向流程动作表(rp_hx_flow_record):流程留痕,action 见 HxFlowStatus.ACT_* */
@Data
@TableName("rp_hx_flow_record")
public class RpHxFlowRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** →rp_project(冗余,按项目聚合全部流程单动作) */
    private Long projectId;
    /** →rp_hx_flow */
    private Long flowId;
    /** submit/withdraw/upload_scan/precheck_reject/reject/resubmit/verify/approve */
    private String action;
    /** 操作人→sys_user */
    private Long operatorId;
    private String operatorName;
    /** 退回意见/核定备注 */
    private String opinion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer deleted;
}
