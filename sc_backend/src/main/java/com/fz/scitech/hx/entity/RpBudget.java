package com.fz.scitech.hx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 项目预算明细表(rp_budget):横向=hx_budget_subject 6 科目,金额单位元(决策#25) */
@Data
@TableName("rp_budget")
public class RpBudget {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** →rp_project */
    private Long projectId;
    /** 预算科目(横向6科目:业务费/劳务费/绩效/业务接待费/协作费/其它) */
    private String subject;
    /** 金额(元) */
    private BigDecimal amount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer deleted;
}
