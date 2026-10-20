package com.fz.scitech.hx.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 预算行(横向 6 科目,金额单位元) */
@Data
public class BudgetDTO {
    private String subject;
    private BigDecimal amount;
    private Integer unrestricted;  // 1=合同约定不受限(不计入合计,打印出口径文字,金额置0)
}
