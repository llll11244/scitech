package com.fz.scitech.hx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 横向项目扩展表(rp_hx_project):与 rp_project 1:1,主键=外键 project_id 非自增 */
@Data
@TableName("rp_hx_project")
public class RpHxProject {

    /** →rp_project.id(1:1) */
    @TableId(value = "project_id", type = IdType.INPUT)
    private Long projectId;
    /** 字典hx_project_type:技术开发/技术咨询/技术服务 */
    private String projectType;
    private String clientName;
    /** 字典client_nature:政府部门/企业/事业单位/研究机构/其他 */
    private String clientNature;
    /** 合同经费(元) */
    private BigDecimal contractAmount;
    /** 合同开始 */
    private LocalDate startDate;
    /** 合同结束 */
    private LocalDate endDate;
    /** 以学校设备场地为主0/1 */
    private Integer useSchoolAsset;
    /** 是否收管理费0/1 */
    private Integer chargeMgmtFee;
    /** 管理费金额(元) */
    private BigDecimal mgmtFeeAmount;
    private String leaderEmail;
    /** 项目简介(200-300字,登记表) */
    private String projectSummary;
    /** 项目计划进度(200-300字,登记表) */
    private String progressPlan;
    /** 立项登记日期(科技处确认时写入) */
    private LocalDate hxRegisterDate;
    /** 核定到账金额(元,验收时定格) */
    private BigDecimal verifiedAmount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer deleted;
}
