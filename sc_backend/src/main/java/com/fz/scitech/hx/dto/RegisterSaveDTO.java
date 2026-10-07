package com.fz.scitech.hx.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 登记一次提交:主表+扩展+成员+预算 */
@Data
public class RegisterSaveDTO {
    private Long flowId;       // 空=新建草稿
    private String name;       // 项目名称(rp_project.name)
    private String leaderName;
    private Long deptId;
    private Hx hx;
    private List<MemberDTO> members;
    private List<BudgetDTO> budgets;

    @Data
    public static class Hx {
        private String projectType;
        private String clientName;
        private String clientNature;
        private BigDecimal contractAmount;
        private String startDate;   // yyyy-MM-dd
        private String endDate;
        private Integer useSchoolAsset;
        private Integer chargeMgmtFee;
        private BigDecimal mgmtFeeAmount;
        private String leaderEmail;
        private String projectSummary;
        private String progressPlan;
    }
}
