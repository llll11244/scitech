package com.fz.scitech.hx.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 验收单一次保存:单据字段+成员全量覆盖(教师/校外补 ratio/amount,学生补 studentNo/mentor) */
@Data
public class AcceptanceSaveDTO {
    private Long flowId;
    private Long projectId;
    private BigDecimal usedAmount;
    private String finishDate;       // 项目完成时间 yyyy-MM-dd(教师填报)
    private Integer isOnTime;
    private String outcomeForm;      // 逗号分隔多选
    private String acceptSummary;
    private String reportContent;    // \n\n 分段
    private List<MemberDTO> members; // 全量覆盖:教师/校外(补 ratio/amount)+学生(studentNo/mentor)
}
