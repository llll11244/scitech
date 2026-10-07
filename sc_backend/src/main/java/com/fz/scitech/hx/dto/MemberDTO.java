package com.fz.scitech.hx.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 成员行(登记=基础五字段;验收补 ratio/amount;学生补 studentNo/mentor) */
@Data
public class MemberDTO {
    private String memberType;   // 教师/学生/校外人员
    private String name;
    private String unit;
    private String title;
    private String major;
    private String duty;
    private String studentNo;
    private String mentor;
    private BigDecimal ratio;    // 验收补录
    private BigDecimal amount;   // 验收补录
    private Integer sortNo;
}
