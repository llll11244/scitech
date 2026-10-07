package com.fz.scitech.hx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 到账明细表(rp_funding_record):admin 分批录入教师只读,累计实时 SUM 不冗余(决策#16)。
 *  Task20 仅用于删登记草稿的"已有到账记录禁删"判定(决策#34-⑤),录入接口 N4 建。 */
@Data
@TableName("rp_funding_record")
public class RpFundingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** →rp_project(横向行,草稿期即存在,决策#20) */
    private Long projectId;
    /** 本笔到账(元) */
    private BigDecimal amount;
    /** 到账日期 */
    private LocalDate receivedDate;
    /** 付款方(默认=委托单位,可改) */
    private String payer;
    /** 银行流水号(选填) */
    private String bankFlowNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer deleted;
}
