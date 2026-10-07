package com.fz.scitech.hx.dto;

import com.fz.scitech.hx.entity.*;
import com.fz.scitech.platform.attachment.RpAttachment;
import lombok.Data;
import java.util.List;

/** 流程详情:单据+项目+生效附件+时间线 */
@Data
public class FlowDetailVO {
    private RpHxFlow flow;
    private RpProject project;
    private RpHxProject hx;
    private List<RpMember> members;
    private List<RpBudget> budgets;
    private List<RpAttachment> attachments; // 仅生效件(deleted=0,作废件查不出=设计§3.2"保留文件不入列表")
    private List<RpHxFlowRecord> records;
    private String deliverPdfVisible;       // sys_config 读数,前端显隐 PDF 入口
    private String newLeaderName;           // 变更单回显:变更后负责人姓名(按 new_leader_id 查 sys_user,N4 Task26)
}
