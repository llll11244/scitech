package com.fz.scitech.hx.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 预算行(横向 6 科目,金额单位元) */
@Data
public class BudgetDTO {
    private String subject;
    private BigDecimal amount;
}
