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
        // mgmtFeeAmount 已收归后端:比例=科技处配置(sys_config),金额=合同经费×比例自动换算,不入 DTO
        private String leaderEmail;
        private String leaderPhone;   // 联系电话(默认带出本人手机号,可改)
        private String projectSummary;
        private String progressPlan;
    }
}
