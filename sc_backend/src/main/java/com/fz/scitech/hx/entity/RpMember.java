package com.fz.scitech.hx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 项目成员表(rp_member):教师/学生/校外人员共用,member_type 字典 */
@Data
@TableName("rp_member")
public class RpMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** →rp_project */
    private Long projectId;
    /** 字典member_type:教师/学生/校外人员 */
    private String memberType;
    private String name;
    private String unit;
    /** 职称(字典;纵向阶段用) */
    private String title;
    /** 学位(字典;纵向阶段用) */
    private String degree;
    /** 专业/学科专业(登记表/验收表成员栏) */
    private String major;
    /** 承担本项目的工作任务(描述性文本) */
    private String duty;
    /** 学号(学生成员) */
    private String studentNo;
    /** 指导老师(学生成员) */
    private String mentor;
    /** 承担任务比例%(验收分工) */
    private BigDecimal ratio;
    /** 承担金额(元,验收分工) */
    private BigDecimal amount;
    /** 排序号(平铺契约:调用方按 sort_no 排序后传入) */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer deleted;
}
