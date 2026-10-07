package com.fz.scitech.hx.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 变更单一次保存:change_type 多选驱动必填联动(联动规则见 HxService.validateChange) */
@Data
public class ChangeSaveDTO {
    private Long flowId;        // 空新建
    private Long projectId;
    private String changeType;      // 逗号分隔多选(字典 change_type)
    private String changeContent;
    private String changeReason;
    private BigDecimal newAmount;   // 含"项目经费"必填
    private String newEndDate;      // 含"执行周期"必填(yyyy-MM-dd)
    private Long newLeaderId;       // 含"项目负责人"必填,须已有账号(userMapper 校验)
}
