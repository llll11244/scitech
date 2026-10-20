package com.fz.scitech.hx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 横向流程单主表(rp_hx_flow):三流程共用,status 见 HxFlowStatus */
@Data
@TableName("rp_hx_flow")
public class RpHxFlow {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** →rp_project */
    private Long projectId;
    /** register登记/change变更/acceptance验收 */
    private String flowType;
    /** 0草稿/1已提交待回扫/2已回扫待审核/3通过/4退回 */
    private Integer status;
    /** 字典hx_change_type,逗号分隔多选 */
    private String changeType;
    private String changeReason;
    private String changeContent;
    /** 变更后经费(元,change_type含项目经费时必填;字典类型名已改hx_change_type) */
    private BigDecimal newAmount;
    /** 变更后结束日(含执行周期时必填) */
    private LocalDate newEndDate;
    /** 变更后负责人→sys_user(含负责人变更时必填,须已有账号) */
    private Long newLeaderId;
    /** 变更前字段快照(改写项目字段前存) */
    private String beforeJson;
    /** 项目完成情况总结(验收单) */
    private String acceptSummary;
    /** 是否按期完成0/1(验收单) */
    private Integer isOnTime;
    /** 成果形式(字典hx_outcome_form,逗号分隔多选) */
    private String outcomeForm;
    /** 使用经费(元,验收单) */
    private BigDecimal usedAmount;
    /** 项目完成时间(验收单教师填报;区别rp_project.finish_date=审批结题日) */
    private LocalDate finishDate;
    /** 附件4报告正文(纯文本,\n\n 分段) */
    private String reportContent;
    private LocalDateTime submitTime;
    private LocalDateTime scanTime;
    private LocalDateTime confirmTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer deleted;
}
