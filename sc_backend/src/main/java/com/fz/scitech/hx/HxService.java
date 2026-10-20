package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fz.scitech.common.BizException;
import com.fz.scitech.hx.dto.AcceptanceSaveDTO;
import com.fz.scitech.hx.dto.BudgetDTO;
import com.fz.scitech.hx.dto.ChangeSaveDTO;
import com.fz.scitech.hx.dto.FlowDetailVO;
import com.fz.scitech.hx.dto.MemberDTO;
import com.fz.scitech.hx.dto.RegisterSaveDTO;
import com.fz.scitech.hx.entity.RpBudget;
import com.fz.scitech.hx.entity.RpFundingRecord;
import com.fz.scitech.hx.entity.RpHxFlow;
import com.fz.scitech.hx.entity.RpHxFlowRecord;
import com.fz.scitech.hx.entity.RpHxProject;
import com.fz.scitech.hx.entity.RpMember;
import com.fz.scitech.hx.entity.RpProject;
import com.fz.scitech.hx.mapper.RpBudgetMapper;
import com.fz.scitech.hx.mapper.RpFundingRecordMapper;
import com.fz.scitech.hx.mapper.RpHxFlowMapper;
import com.fz.scitech.hx.mapper.RpHxFlowRecordMapper;
import com.fz.scitech.hx.mapper.RpHxProjectMapper;
import com.fz.scitech.hx.mapper.RpMemberMapper;
import com.fz.scitech.hx.mapper.RpProjectMapper;
import com.fz.scitech.platform.attachment.RpAttachment;
import com.fz.scitech.platform.attachment.mapper.RpAttachmentMapper;
import com.fz.scitech.system.entity.SysConfig;
import com.fz.scitech.system.entity.SysDept;
import com.fz.scitech.system.entity.SysDict;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysConfigMapper;
import com.fz.scitech.system.mapper.SysDeptMapper;
import com.fz.scitech.system.mapper.SysDictMapper;
import com.fz.scitech.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 横向教师端三流程业务。状态迁移一律条件更新 WHERE status=旧值(决策#34-②),
 * 影响行数 0 抛「状态已变化」防并发双提;教师越权防线=每条查询/更新强制 apply_user_id 归属校验(spec §6.4)。
 * Task20 登记流程;Task21 变更/验收:变更在途唯一+验收每项目一张(spec §6.1),
 * draft 不校验项目确认态(教师可提前起草),submit 强校验 audit_status=2。
 * Task24:退回单(status=4)允许教师编辑草稿数据(改数据→resubmit 重交,也可直接重交,
 * 编辑本身不迁移状态,4→1 由 resubmit 显式做);科技处审批副作用见同包 HxAuditService。
 */
@Slf4j
@Service
public class HxService {

    /** 扫描件文件名时间戳:毫秒级(Task25 Minor⑤ 收口,Task28)——同秒重传同 docType 会物理覆盖旧件 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    /** 扫描件允许扩展名(N4 注意⑦,Task28):与 FundingService.ATTACH_EXTS 同口径 */
    private static final List<String> SCAN_EXTS = List.of("pdf", "png", "jpg", "jpeg");

    private final RpProjectMapper projectMapper;
    private final RpHxProjectMapper hxMapper;
    private final RpHxFlowMapper flowMapper;
    private final RpHxFlowRecordMapper recordMapper;
    private final RpMemberMapper memberMapper;
    private final RpBudgetMapper budgetMapper;
    private final RpAttachmentMapper attachmentMapper;
    private final RpFundingRecordMapper fundingMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysDictMapper dictMapper;
    private final SysConfigMapper configMapper;
    private final MgmtFeePolicy mgmtFeePolicy;
    private final ObjectMapper objectMapper;

    @Value("${sc.upload-dir:./upload}")
    private String uploadDir;

    public HxService(RpProjectMapper projectMapper, RpHxProjectMapper hxMapper, RpHxFlowMapper flowMapper,
                     RpHxFlowRecordMapper recordMapper, RpMemberMapper memberMapper, RpBudgetMapper budgetMapper,
                     RpAttachmentMapper attachmentMapper, RpFundingRecordMapper fundingMapper,
                     SysDeptMapper deptMapper, SysUserMapper userMapper, SysDictMapper dictMapper,
                     SysConfigMapper configMapper, MgmtFeePolicy mgmtFeePolicy, ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.hxMapper = hxMapper;
        this.flowMapper = flowMapper;
        this.recordMapper = recordMapper;
        this.memberMapper = memberMapper;
        this.budgetMapper = budgetMapper;
        this.attachmentMapper = attachmentMapper;
        this.fundingMapper = fundingMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.dictMapper = dictMapper;
        this.configMapper = configMapper;
        this.mgmtFeePolicy = mgmtFeePolicy;
        this.objectMapper = objectMapper;
    }

    // ---------- 登记草稿 ----------
    /**
     * 幂等:flowId 空新建(kind=2 草稿行+register 草稿流程单),非空整体覆盖。
     * HTML转正(2026-10-21):编辑门槛 {0,4}→{0,1,2,4}(审批通过前可改,决策②)。
     */
    @Transactional
    public Long saveRegisterDraft(Long uid, String username, RegisterSaveDTO dto) {
        validateRegister(dto);
        if (dto.getFlowId() == null) {
            RpProject project = new RpProject();
            project.setKind(2);
            project.setAuditStatus(0);
            project.setApplyUserId(uid);
            project.setCreateBy(username);
            // name 为非空列,插入前必填(其余列可空)
            project.setName(dto.getName());
            project.setLeaderName(dto.getLeaderName());
            project.setDeptId(dto.getDeptId());
            projectMapper.insert(project);
            RpHxFlow flow = new RpHxFlow();
            flow.setProjectId(project.getId());
            flow.setFlowType("register");
            flow.setStatus(HxFlowStatus.DRAFT);
            flow.setCreateBy(username);
            flowMapper.insert(flow);
            applyRegisterData(project.getId(), dto, username);
            return flow.getId();
        }
        RpHxFlow flow = ownedFlow(uid, dto.getFlowId());
        applyRegisterEdit(flow, dto, username, "仅审批通过前可编辑");
        return flow.getId();
    }

    /**
     * 编辑态持久化(教师 1/2/4 态代保存与 admin 代改共用,HxAuditService 调):
     * project 三字段 + 扩展/成员/预算整体覆盖;guardEditable 条件占位防并发竞态(决策#34-②)。
     */
    void applyRegisterEdit(RpHxFlow flow, RegisterSaveDTO dto, String operator, String rejectMsg) {
        if (!editableBeforeApprove(flow.getStatus())) {
            throw new BizException(rejectMsg);
        }
        guardEditable(flow.getId(), rejectMsg);
        RpProject project = projectMapper.selectById(flow.getProjectId());
        project.setName(dto.getName());
        project.setLeaderName(dto.getLeaderName());
        project.setDeptId(dto.getDeptId());
        // 编辑路径一律显式 .set(与 hx 扩展行/F1 同口径):NOT_NULL 策略下 updateById 跳过
        // null 字段;name/leaderName/deptId 经 validateRegister 必填非空,显式写库防御后续字段放开
        projectMapper.update(null, new LambdaUpdateWrapper<RpProject>()
                .eq(RpProject::getId, project.getId())
                .set(RpProject::getName, project.getName())
                .set(RpProject::getLeaderName, project.getLeaderName())
                .set(RpProject::getDeptId, project.getDeptId()));
        applyRegisterData(project.getId(), dto, operator);
    }

    /** 扩展段/成员/预算整体覆盖(新建与编辑共用;管理费金额后端换算,提交即快照) */
    private void applyRegisterData(Long projectId, RegisterSaveDTO dto, String operator) {
        RpHxProject hx = hxMapper.selectById(projectId);
        boolean hxNew = hx == null;
        if (hxNew) {
            hx = new RpHxProject();
            hx.setProjectId(projectId);
            hx.setCreateBy(operator);
        }
        copyHx(dto.getHx(), hx);
        // 管理费金额后端换算(比例=科技处配置,教师不可设):覆盖入参,收取关掉→null;每次保存重算,提交即快照
        hx.setMgmtFeeAmount(mgmtFeePolicy.compute(hx.getChargeMgmtFee(), hx.getContractAmount()));
        if (hxNew) {
            hxMapper.insert(hx);
        } else {
            // 同 F1:NOT_NULL 策略下 updateById 跳过实体 null 字段——教师清空「简介/进度/邮箱」等
            // 可选字段后旧值残留——表单字段显式 .set 整体覆盖(null 也写);
            // hxRegisterDate/verifiedAmount 非表单字段(科技处确认/验收时写入)不在覆盖之列
            hxMapper.update(null, new LambdaUpdateWrapper<RpHxProject>()
                    .eq(RpHxProject::getProjectId, hx.getProjectId())
                    .set(RpHxProject::getProjectType, hx.getProjectType())
                    .set(RpHxProject::getClientName, hx.getClientName())
                    .set(RpHxProject::getClientNature, hx.getClientNature())
                    .set(RpHxProject::getContractAmount, hx.getContractAmount())
                    .set(RpHxProject::getStartDate, hx.getStartDate())
                    .set(RpHxProject::getEndDate, hx.getEndDate())
                    .set(RpHxProject::getUseSchoolAsset, hx.getUseSchoolAsset())
                    .set(RpHxProject::getChargeMgmtFee, hx.getChargeMgmtFee())
                    .set(RpHxProject::getMgmtFeeAmount, hx.getMgmtFeeAmount())
                    .set(RpHxProject::getLeaderEmail, hx.getLeaderEmail())
                    .set(RpHxProject::getProjectSummary, hx.getProjectSummary())
                    .set(RpHxProject::getProgressPlan, hx.getProgressPlan()));
        }
        replaceMembers(projectId, dto.getMembers(), operator);
        replaceBudgets(projectId, dto.getBudgets(), operator);
    }

    /**
     * 必填+字数校验(保存/提交/admin 代改同口径):名称/负责人/院部/类型/委托单位/性质/经费/起止;
     * 字数上限与前端 maxlength 同值(HTML转正 决策:后端拒保存)。
     */
    void validateRegister(RegisterSaveDTO dto) {
        if (isBlank(dto.getName()) || isBlank(dto.getLeaderName()) || dto.getDeptId() == null) {
            throw new BizException("项目名称/负责人/院部必填");
        }
        RegisterSaveDTO.Hx h = dto.getHx();
        if (h == null || isBlank(h.getProjectType()) || isBlank(h.getClientName())
                || isBlank(h.getClientNature()) || h.getContractAmount() == null
                || isBlank(h.getStartDate()) || isBlank(h.getEndDate())) {
            throw new BizException("项目类型/委托单位/性质/合同经费/起止时间必填");
        }
        if (h.getContractAmount().signum() <= 0) {
            throw new BizException("合同经费必须大于 0");
        }
        lenCheck(dto.getName(), 100, "项目名称");
        lenCheck(dto.getLeaderName(), 64, "负责人姓名");
        lenCheck(h.getClientName(), 255, "委托单位名称");
        lenCheck(h.getLeaderEmail(), 128, "电子邮箱");
        lenCheck(h.getProjectSummary(), 800, "项目简介");
        lenCheck(h.getProgressPlan(), 800, "项目计划进度");
    }

    /** 规则R1(HTML转正 决策④):非「不受限」科目合计=合同经费;提交/admin 代改强制,draft 允许半成品 */
    void validateRegisterBudgets(Long projectId, BigDecimal contractAmount) {
        BigDecimal sum = BigDecimal.ZERO;
        for (RpBudget b : budgetMapper.selectList(new LambdaQueryWrapper<RpBudget>()
                .eq(RpBudget::getProjectId, projectId))) {
            boolean free = b.getUnrestricted() != null && b.getUnrestricted() == 1;
            if (!free && b.getAmount() != null) {
                sum = sum.add(b.getAmount());
            }
        }
        if (sum.compareTo(contractAmount) != 0) {
            throw new BizException("预算合计(" + plain(sum) + ")须等于合同经费(" + plain(contractAmount)
                    + ")元;勾选「合同约定不受限」的科目不计入合计");
        }
    }

    /** 验收必填(提交/admin 代改强制;与前端 rules 同清单) */
    void validateAcceptanceRequired(RpHxFlow flow) {
        if (flow.getUsedAmount() == null || flow.getIsOnTime() == null || isBlank(flow.getOutcomeForm())
                || isBlank(flow.getAcceptSummary()) || isBlank(flow.getReportContent())) {
            throw new BizException("使用经费/是否按期完成/成果形式/完成情况总结/验收报告正文必填");
        }
        if (flow.getUsedAmount().signum() < 0) {
            throw new BizException("使用经费不能为负数");
        }
    }

    /** 规则R3-R6:验收金额核算(比例合计=100%、金额合计=到账、使用≤到账、无到账拒) */
    void validateAcceptanceAmounts(Long projectId, RpHxFlow flow) {
        BigDecimal received = receivedTotal(projectId);
        if (received == null || received.signum() <= 0) {
            throw new BizException("暂无到账记录,请联系科技处录入到账后再提交验收");
        }
        if (flow.getUsedAmount() != null && flow.getUsedAmount().compareTo(received) > 0) {
            throw new BizException("使用经费(" + plain(flow.getUsedAmount()) + ")不得超过到账经费("
                    + plain(received) + ")元");
        }
        BigDecimal ratioSum = BigDecimal.ZERO;
        BigDecimal amountSum = BigDecimal.ZERO;
        for (RpMember m : memberMapper.selectList(new LambdaQueryWrapper<RpMember>()
                .eq(RpMember::getProjectId, projectId)
                .ne(RpMember::getMemberType, "学生"))) {
            if (m.getRatio() != null) {
                ratioSum = ratioSum.add(m.getRatio());
            }
            if (m.getAmount() != null) {
                amountSum = amountSum.add(m.getAmount());
            }
        }
        if (ratioSum.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BizException("成员比例合计(" + plain(ratioSum) + "%)须等于 100%");
        }
        if (amountSum.compareTo(received) != 0) {
            throw new BizException("成员金额合计(" + plain(amountSum) + ")须等于到账经费(" + plain(received) + ")元");
        }
    }

    /** 字数硬校验(HTML转正):null 跳过;超限拒保存(与前端 maxlength 同值) */
    static void lenCheck(String v, int max, String label) {
        if (v != null && v.length() > max) {
            throw new BizException(label + "超过 " + max + " 字");
        }
    }

    /** 金额提示用(去尾零,355000.00→355000) */
    static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    /** 审批通过前(0/1/2/4)可编辑(HTML转正 决策②:提交后仍可改,通过即锁定) */
    static boolean editableBeforeApprove(Integer status) {
        return status != null && status != HxFlowStatus.APPROVED;
    }

    // ---------- 提交(生成待签双出) ----------
    @Transactional
    public void submitRegister(Long uid, String username, Long flowId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (flow.getStatus() != HxFlowStatus.DRAFT) {
            throw new BizException("仅草稿可提交");
        }
        RegisterSaveDTO dto = loadRegisterDto(flow.getProjectId()); // 反查实体拼 DTO 复用校验
        validateRegister(dto);
        validateRegisterBudgets(flow.getProjectId(), dto.getHx().getContractAmount());   // 规则R1
        transit(flowId, HxFlowStatus.DRAFT, HxFlowStatus.SUBMITTED);
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId).set(RpHxFlow::getSubmitTime, LocalDateTime.now()));
        projectMapper.update(null, new LambdaUpdateWrapper<RpProject>()
                .eq(RpProject::getId, flow.getProjectId())
                .set(RpProject::getAuditStatus, 1)
                .set(RpProject::getDeclareDate, LocalDate.now()));
        // HTML转正(决策①):待签件=教师浏览器打印 A4/另存 PDF,不再生成 docx 双出
        saveRecord(flow, HxFlowStatus.ACT_SUBMIT, username, uid, null);
    }

    // ---------- 变更草稿 ----------
    /**
     * 变更草稿保存:draft 不校验项目确认态(教师可在登记确认前提前起草,spec §6.1"已登记确认"
     * 是提交生效条件,提交时强校验);同项目在途变更单唯一(0/1/2/4,3 通过后可再发新单)。
     */
    @Transactional
    public Long saveChangeDraft(Long uid, String username, ChangeSaveDTO dto) {
        RpProject project = ownedProject(uid, dto.getProjectId());
        LocalDate newEndDate = parseDate(dto.getNewEndDate());   // 格式先验,再联动必填
        validateChange(dto.getChangeType(), dto.getNewAmount(), newEndDate, dto.getNewLeaderId());
        validateChangeTexts(dto);
        guardChangeUnique(project.getId(), dto.getFlowId());
        RpHxFlow flow;
        if (dto.getFlowId() == null) {
            flow = new RpHxFlow();
            flow.setProjectId(project.getId());
            flow.setFlowType("change");
            flow.setStatus(HxFlowStatus.DRAFT);
            flow.setCreateBy(username);
            applyChangeData(flow, dto);
            flowMapper.insert(flow);
        } else {
            flow = editableDraftFlow(uid, dto.getFlowId(), "change", dto.getProjectId());
            applyChangeEdit(flow, dto, "仅审批通过前可编辑");
        }
        return flow.getId();
    }

    /** 变更文本字数(教师保存与 admin 代改共用) */
    void validateChangeTexts(ChangeSaveDTO dto) {
        lenCheck(dto.getChangeContent(), 500, "变更内容");
        lenCheck(dto.getChangeReason(), 300, "申请变更的原因");
    }

    /** 变更单据字段整体赋值(教师保存与 admin 代改共用) */
    void applyChangeData(RpHxFlow flow, ChangeSaveDTO dto) {
        flow.setChangeType(dto.getChangeType());
        flow.setChangeContent(dto.getChangeContent());
        flow.setChangeReason(dto.getChangeReason());
        flow.setNewAmount(dto.getNewAmount());
        flow.setNewEndDate(parseDate(dto.getNewEndDate()));
        flow.setNewLeaderId(dto.getNewLeaderId());
    }

    /** 变更编辑态持久化(教师 1/2/4 态与 admin 代改共用):条件占位+整体覆盖+触碰 update_time */
    void applyChangeEdit(RpHxFlow flow, ChangeSaveDTO dto, String rejectMsg) {
        if (!editableBeforeApprove(flow.getStatus())) {
            throw new BizException(rejectMsg);
        }
        guardEditable(flow.getId(), rejectMsg);
        applyChangeData(flow, dto);
        updateChangeRow(flow);
    }

    /**
     * 变更编辑路径落库:显式 .set 整体覆盖(null 也写)——NOT_NULL 策略下 updateById 跳过实体 null
     * 字段,教师/admin 勾掉「项目经费/执行周期/负责人」后 DTO 置 null 的字段旧值残留;
     * 显式触碰 update_time=滞后判定依据(HTML转正:回扫后改数据→打印/扫描件差异提示)。
     */
    void updateChangeRow(RpHxFlow flow) {
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flow.getId())
                .set(RpHxFlow::getChangeType, flow.getChangeType())
                .set(RpHxFlow::getChangeContent, flow.getChangeContent())
                .set(RpHxFlow::getChangeReason, flow.getChangeReason())
                .set(RpHxFlow::getNewAmount, flow.getNewAmount())
                .set(RpHxFlow::getNewEndDate, flow.getNewEndDate())
                .set(RpHxFlow::getNewLeaderId, flow.getNewLeaderId())
                .set(RpHxFlow::getUpdateTime, LocalDateTime.now()));
    }

    /** 变更必填联动(包级:admin 代改复用):change_type 含「项目经费」→newAmount;「执行周期」→newEndDate;「项目负责人」→newLeaderId 且须已有账号 */
    void validateChange(String changeType, BigDecimal newAmount, LocalDate newEndDate, Long newLeaderId) {
        if (isBlank(changeType)) {
            throw new BizException("变更类型必填");
        }
        // 逐项 trim:防「项目经费, 执行周期」带空格勾不上(与 RenderModelBuilder.splitTrim 同口径)
        List<String> types = Arrays.stream(changeType.split(",")).map(String::trim).collect(Collectors.toList());
        if (types.contains("项目经费") && newAmount == null) {
            throw new BizException("变更类型含「项目经费」,须填写变更后经费");
        }
        if (newAmount != null && newAmount.signum() <= 0) {
            throw new BizException("变更后经费必须大于 0");
        }
        if (types.contains("执行周期") && newEndDate == null) {
            throw new BizException("变更类型含「执行周期」,须填写变更后结束时间");
        }
        if (types.contains("项目负责人")) {
            if (newLeaderId == null) {
                throw new BizException("变更类型含「项目负责人」,须选择变更后负责人");
            }
            if (userMapper.selectById(newLeaderId) == null) {
                throw new BizException("变更后负责人账号不存在");
            }
        }
    }

    /** 同项目在途变更单唯一:status 0/1/2/4 任一存在即拒(编辑自身除外) */
    private void guardChangeUnique(Long projectId, Long excludeFlowId) {
        Long count = flowMapper.selectCount(new LambdaQueryWrapper<RpHxFlow>()
                .eq(RpHxFlow::getProjectId, projectId)
                .eq(RpHxFlow::getFlowType, "change")
                .in(RpHxFlow::getStatus, HxFlowStatus.DRAFT, HxFlowStatus.SUBMITTED,
                        HxFlowStatus.SCANNED, HxFlowStatus.REJECTED)
                .ne(excludeFlowId != null, RpHxFlow::getId, excludeFlowId));
        if (count != null && count > 0) {
            throw new BizException("该项目已有进行中的变更单");
        }
    }

    // ---------- 变更提交 ----------
    /** 变更提交:audit_status=2 强校验(spec §6.1);submit 时拍 before_json(N4 审批对照依据);0→1+渲染 hx_change */
    @Transactional
    public void submitChange(Long uid, String username, Long flowId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (!"change".equals(flow.getFlowType())) {
            throw new BizException("非变更流程单");
        }
        if (flow.getStatus() != HxFlowStatus.DRAFT) {
            throw new BizException("仅草稿可提交");
        }
        requireConfirmed(flow.getProjectId(), "变更单");
        validateChange(flow.getChangeType(), flow.getNewAmount(), flow.getNewEndDate(), flow.getNewLeaderId());
        String beforeJson = snapshotBefore(flow.getProjectId());
        transit(flowId, HxFlowStatus.DRAFT, HxFlowStatus.SUBMITTED);
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId)
                .set(RpHxFlow::getSubmitTime, LocalDateTime.now())
                .set(RpHxFlow::getBeforeJson, beforeJson));
        // HTML转正(决策①):不再生成 docx 双出
        saveRecord(flow, HxFlowStatus.ACT_SUBMIT, username, uid, null);
    }

    // ---------- 验收草稿 ----------
    /**
     * 验收草稿保存:每项目至多 1 张验收单(任何 status,约束#3——被退回也只能改后重提,不可另起);
     * 成员全量覆盖(教师/校外补 ratio/amount,学生补 studentNo/mentor,replaceMembers 全字段拷贝)。
     */
    @Transactional
    public Long saveAcceptanceDraft(Long uid, String username, AcceptanceSaveDTO dto) {
        RpProject project = ownedProject(uid, dto.getProjectId());
        validateAcceptanceTexts(dto);
        guardAcceptanceSingle(project.getId(), dto.getFlowId());
        RpHxFlow flow;
        if (dto.getFlowId() == null) {
            flow = new RpHxFlow();
            flow.setProjectId(project.getId());
            flow.setFlowType("acceptance");
            flow.setStatus(HxFlowStatus.DRAFT);
            flow.setCreateBy(username);
            applyAcceptanceData(flow, dto);
            flowMapper.insert(flow);
            replaceMembers(project.getId(), dto.getMembers(), username);
        } else {
            flow = editableDraftFlow(uid, dto.getFlowId(), "acceptance", dto.getProjectId());
            applyAcceptanceEdit(flow, dto, username, "仅审批通过前可编辑");
        }
        return flow.getId();
    }

    /** 验收文本字数(保存级;必填/金额在提交与 admin 代改时校验) */
    void validateAcceptanceTexts(AcceptanceSaveDTO dto) {
        lenCheck(dto.getAcceptSummary(), 1000, "项目完成情况总结");
        lenCheck(dto.getReportContent(), 20000, "验收报告正文");
    }

    /** 验收编辑态持久化(教师 1/2/4 态与 admin 代改共用):条件占位+整体覆盖+成员全量+触碰 update_time */
    void applyAcceptanceEdit(RpHxFlow flow, AcceptanceSaveDTO dto, String operator, String rejectMsg) {
        if (!editableBeforeApprove(flow.getStatus())) {
            throw new BizException(rejectMsg);
        }
        guardEditable(flow.getId(), rejectMsg);
        applyAcceptanceData(flow, dto);
        updateAcceptanceRow(flow);
        replaceMembers(flow.getProjectId(), dto.getMembers(), operator);
    }

    /** 验收单据字段整体赋值(教师保存与 admin 代改共用) */
    void applyAcceptanceData(RpHxFlow flow, AcceptanceSaveDTO dto) {
        flow.setUsedAmount(dto.getUsedAmount());
        flow.setFinishDate(parseDate(dto.getFinishDate()));
        flow.setIsOnTime(dto.getIsOnTime());
        flow.setOutcomeForm(dto.getOutcomeForm());
        flow.setAcceptSummary(dto.getAcceptSummary());
        flow.setReportContent(dto.getReportContent());
    }

    /** 验收编辑路径落库:显式 .set 整体覆盖(null 也写)+触碰 update_time(滞后判定) */
    void updateAcceptanceRow(RpHxFlow flow) {
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flow.getId())
                .set(RpHxFlow::getUsedAmount, flow.getUsedAmount())
                .set(RpHxFlow::getFinishDate, flow.getFinishDate())
                .set(RpHxFlow::getIsOnTime, flow.getIsOnTime())
                .set(RpHxFlow::getOutcomeForm, flow.getOutcomeForm())
                .set(RpHxFlow::getAcceptSummary, flow.getAcceptSummary())
                .set(RpHxFlow::getReportContent, flow.getReportContent())
                .set(RpHxFlow::getUpdateTime, LocalDateTime.now()));
    }

    /** 每项目至多 1 张验收单(任何 status,约束#3;编辑自身除外) */
    private void guardAcceptanceSingle(Long projectId, Long excludeFlowId) {
        Long count = flowMapper.selectCount(new LambdaQueryWrapper<RpHxFlow>()
                .eq(RpHxFlow::getProjectId, projectId)
                .eq(RpHxFlow::getFlowType, "acceptance")
                .ne(excludeFlowId != null, RpHxFlow::getId, excludeFlowId));
        if (count != null && count > 0) {
            throw new BizException("该项目已有验收单(每项目至多一张)");
        }
    }

    // ---------- 验收提交 ----------
    /** 验收提交:audit_status=2 强校验;0→1;渲染两张待签(hx_acceptance+hx_report,双出×2=4 附件) */
    @Transactional
    public void submitAcceptance(Long uid, String username, Long flowId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (!"acceptance".equals(flow.getFlowType())) {
            throw new BizException("非验收流程单");
        }
        if (flow.getStatus() != HxFlowStatus.DRAFT) {
            throw new BizException("仅草稿可提交");
        }
        requireConfirmed(flow.getProjectId(), "验收单");
        validateAcceptanceRequired(flow);
        validateAcceptanceAmounts(flow.getProjectId(), flow);
        transit(flowId, HxFlowStatus.DRAFT, HxFlowStatus.SUBMITTED);
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId).set(RpHxFlow::getSubmitTime, LocalDateTime.now()));
        // HTML转正(决策①):待签两张(验收登记表+验收报告)=教师浏览器分别打印,不再生成 docx 双出
        saveRecord(flow, HxFlowStatus.ACT_SUBMIT, username, uid, null);
    }

    /**
     * 同一流程单旧待签/扫描件作废(逻辑删附件行,文件保留不入列表=设计§3.2)。
     * 完整前缀 hx/{projectId}/{dir}/{flowId}/ 定位(与 writeDeliver 同口径):RpAttachment 无 flowId 列,
     * 整段匹配杜绝「pid 恰等于 fid 数值」时按 "/{fid}/" 段匹配的跨流程误配;渲染件 dir=flowType
     * (验收两张同前缀天然全覆盖),扫描件 dir=scan;docType 非空仅作废同类型扫描件(决策#34-④)。
     */
    private void invalidateOld(RpHxFlow flow, String dir, String docType, String... bizTypes) {
        attachmentMapper.delete(new LambdaQueryWrapper<RpAttachment>()
                .eq(RpAttachment::getProjectId, flow.getProjectId())
                .in(RpAttachment::getBizType, Arrays.asList(bizTypes))
                .eq(docType != null, RpAttachment::getDocType, docType)
                .likeRight(RpAttachment::getFilePath,
                        "hx/" + flow.getProjectId() + "/" + dir + "/" + flow.getId() + "/"));
    }

    // ---------- 撤回(三流程共用) ----------
    /** 已提交(1)→草稿(0);已回扫(2)→已提交(1,重传前须先撤回重盖章场景)。留痕=ACT_WITHDRAW 入时间线 */
    @Transactional
    public void withdraw(Long uid, String username, Long flowId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (flow.getStatus() == HxFlowStatus.SUBMITTED) {
            transit(flowId, HxFlowStatus.SUBMITTED, HxFlowStatus.DRAFT);
        } else if (flow.getStatus() == HxFlowStatus.SCANNED) {
            transit(flowId, HxFlowStatus.SCANNED, HxFlowStatus.SUBMITTED);
        } else {
            throw new BizException("当前状态不可撤回");
        }
        saveRecord(flow, HxFlowStatus.ACT_WITHDRAW, username, uid, null);
    }

    // ---------- 退回修改重交(三流程共用,Task24) ----------
    /**
     * 退回单重交(spec §3.2):status 4→1;旧扫描件一并作废(需重新盖章回扫)。
     * HTML转正(决策①):不再重渲待签 docx,教师重交后自行重新打印;教师改数据走
     * saveXxxDraft(0/1/2/4 可编辑),不改也可直接重交。
     */
    @Transactional
    public void resubmit(Long uid, String username, Long flowId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (flow.getStatus() != HxFlowStatus.REJECTED) {
            throw new BizException("仅退回单可重交");
        }
        transit(flowId, HxFlowStatus.REJECTED, HxFlowStatus.SUBMITTED);
        invalidateOld(flow, "scan", null, "hx_scan");   // 旧扫描件作废(重交须重新盖章回扫)
        saveRecord(flow, HxFlowStatus.ACT_RESUBMIT, username, uid, null);
    }

    // ---------- 回扫 ----------
    /**
     * 盖章扫描件上传:status 1→2;已回扫(2)重传=按 docType 覆盖,旧件作废(决策#34-④ 每类仅最新一份生效)。
     * 存盘 upload/hx/{pid}/scan/{flowId}/scan_{ts}.{ext}(目录独立于渲染件)。
     */
    @Transactional
    public Long uploadScan(Long uid, String username, Long flowId, MultipartFile file, String docType) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        // 输入校验先行(空文件/扩展名白名单),再进状态机——格式非法在任何状态下同一句拒绝(Task28)
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择扫描件文件");
        }
        String ext = ext(file.getOriginalFilename());
        if (!SCAN_EXTS.contains(ext)) {
            throw new BizException("扫描件格式仅支持:" + String.join("/", SCAN_EXTS));
        }
        int st = flow.getStatus();
        if (st != HxFlowStatus.SUBMITTED && st != HxFlowStatus.SCANNED) {
            throw new BizException("仅已提交状态可上传扫描件");
        }
        validateScanDocType(flow.getFlowType(), docType);
        if (st == HxFlowStatus.SUBMITTED) {
            transit(flowId, HxFlowStatus.SUBMITTED, HxFlowStatus.SCANNED);
        } else {
            // 已回扫(2)重传覆盖分支:条件占位封竞态——审批通过(2→3)抢先则 0 行即拒(决策#34-②)
            guardStatus(flowId, HxFlowStatus.SCANNED, "状态已变化,请刷新后重试");
        }
        // 先作废旧件再插新件(新件路径同前缀,顺序反了会把自己删掉)
        invalidateOld(flow, "scan", docType, "hx_scan");
        File dir = new File(uploadDir, "hx/" + flow.getProjectId() + "/scan/" + flowId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("扫描件目录创建失败:" + dir);
        }
        File dest = new File(dir, "scan_" + LocalDateTime.now().format(TS) + "." + ext);
        try (InputStream in = file.getInputStream()) {
            java.nio.file.Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException("扫描件保存失败:" + e.getMessage());
        }
        RpAttachment att = new RpAttachment();
        att.setProjectId(flow.getProjectId());
        att.setBizType("hx_scan");
        att.setDocType(docType);
        att.setFileName(file.getOriginalFilename());
        att.setFilePath(toRelative(dest));
        att.setFileSize(dest.length());
        att.setUploaderId(uid);
        att.setCreateBy(username);
        attachmentMapper.insert(att);
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId).set(RpHxFlow::getScanTime, LocalDateTime.now()));
        saveRecord(flow, HxFlowStatus.ACT_UPLOAD_SCAN, username, uid, null);
        return att.getId();
    }

    /** docType 合法集=字典 hx_scan_doc_type 启用项,且与流程类型匹配(acceptance 另收验收报告) */
    private void validateScanDocType(String flowType, String docType) {
        List<String> legal = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictType, "hx_scan_doc_type").eq(SysDict::getStatus, 1))
                .stream().map(SysDict::getDictLabel).collect(Collectors.toList());
        if (docType == null || !legal.contains(docType)) {
            throw new BizException("扫描件类型不合法,可选:" + String.join("/", legal));
        }
        boolean match = switch (flowType) {
            case "register" -> "登记表".equals(docType);
            case "change" -> "变更表".equals(docType);
            case "acceptance" -> "验收登记表".equals(docType) || "验收报告".equals(docType);
            default -> false;
        };
        if (!match) {
            throw new BizException("扫描件类型与流程单不匹配:" + docType);
        }
    }

    // ---------- 删草稿 ----------
    /**
     * 删登记草稿:连带逻辑删 项目/扩展/成员/预算/附件 行(文件保留,决策#34-⑤⑧);
     * 已有到账记录禁删(到账明细有处可挂的前提,决策#20/#34-⑤)。
     * change/acceptance 草稿(Task21)仅删流程单本身,项目行不动。
     */
    @Transactional
    public void deleteDraft(Long uid, Long flowId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (flow.getStatus() != HxFlowStatus.DRAFT) {
            throw new BizException("仅草稿可删除");
        }
        guardDraft(flowId, "仅草稿可删除");   // 条件占位防并发提交竞态(决策#34-②)
        if ("register".equals(flow.getFlowType())) {
            Long projectId = flow.getProjectId();
            if (fundingMapper.selectCount(new LambdaQueryWrapper<RpFundingRecord>()
                    .eq(RpFundingRecord::getProjectId, projectId)) > 0) {
                throw new BizException("项目已有到账记录,禁止删除");
            }
            attachmentMapper.delete(new LambdaQueryWrapper<RpAttachment>()
                    .eq(RpAttachment::getProjectId, projectId));
            budgetMapper.delete(new LambdaQueryWrapper<RpBudget>()
                    .eq(RpBudget::getProjectId, projectId));
            memberMapper.delete(new LambdaQueryWrapper<RpMember>()
                    .eq(RpMember::getProjectId, projectId));
            hxMapper.deleteById(projectId);
            projectMapper.deleteById(projectId);
        }
        flowMapper.deleteById(flowId);
    }

    // ---------- 我的项目 ----------
    /** 教师本人横向项目聚合:项目基本列+各流程单 {flowId,flowType,status,时间} */
    public List<Map<String, Object>> myProjects(Long uid) {
        List<RpProject> projects = projectMapper.selectList(new LambdaQueryWrapper<RpProject>()
                .eq(RpProject::getApplyUserId, uid)
                .eq(RpProject::getKind, 2)
                .orderByDesc(RpProject::getId));
        if (projects.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RpHxFlow>> byProject = flowMapper.selectList(new LambdaQueryWrapper<RpHxFlow>()
                        .in(RpHxFlow::getProjectId,
                                projects.stream().map(RpProject::getId).collect(Collectors.toList())))
                .stream().collect(Collectors.groupingBy(RpHxFlow::getProjectId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RpProject p : projects) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", p.getId());
            row.put("name", p.getName());
            row.put("leaderName", p.getLeaderName());
            row.put("auditStatus", p.getAuditStatus());
            row.put("approvalNo", p.getApprovalNo());
            row.put("createTime", p.getCreateTime());
            List<Map<String, Object>> flows = new ArrayList<>();
            for (RpHxFlow f : byProject.getOrDefault(p.getId(), List.of())) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("flowId", f.getId());
                fm.put("flowType", f.getFlowType());
                fm.put("status", f.getStatus());
                fm.put("submitTime", f.getSubmitTime());
                fm.put("scanTime", f.getScanTime());
                flows.add(fm);
            }
            row.put("flows", flows);
            rows.add(row);
        }
        return rows;
    }

    // ---------- 流程详情 ----------
    /** 教师=本人流程单(ownedFlow 防线);admin/viewer 直查。附件列表只含生效件(作废件查不出,§3.2) */
    public FlowDetailVO flowDetail(Long uid, String role, Long flowId) {
        RpHxFlow flow = accessibleFlow(uid, role, flowId);
        FlowDetailVO vo = new FlowDetailVO();
        vo.setFlow(flow);
        vo.setProject(projectMapper.selectById(flow.getProjectId()));
        vo.setHx(hxMapper.selectById(flow.getProjectId()));
        vo.setMembers(memberMapper.selectList(new LambdaQueryWrapper<RpMember>()
                .eq(RpMember::getProjectId, flow.getProjectId()).orderByAsc(RpMember::getSortNo)));
        vo.setBudgets(budgetMapper.selectList(new LambdaQueryWrapper<RpBudget>()
                .eq(RpBudget::getProjectId, flow.getProjectId())));
        vo.setAttachments(attachmentMapper.selectList(new LambdaQueryWrapper<RpAttachment>()
                .eq(RpAttachment::getProjectId, flow.getProjectId()).orderByDesc(RpAttachment::getId)));
        vo.setRecords(recordMapper.selectList(new LambdaQueryWrapper<RpHxFlowRecord>()
                .eq(RpHxFlowRecord::getFlowId, flowId).orderByAsc(RpHxFlowRecord::getId)));
        vo.setDeliverPdfVisible(readConfig("deliver_pdf_visible"));
        // 验收单:到账累计只读带出(金额校验 R4 基准,教师不可填)
        if ("acceptance".equals(flow.getFlowType())) {
            vo.setReceivedTotal(receivedTotal(flow.getProjectId()));
        }
        // 变更单回显:变更后负责人姓名(N4 Task26,N3 缓期项——审批对照/教师端展示用)
        if (flow.getNewLeaderId() != null) {
            SysUser newLeader = userMapper.selectById(flow.getNewLeaderId());
            vo.setNewLeaderName(newLeader == null ? null : newLeader.getRealName());
        }
        return vo;
    }

    /** 项目扩展行必在(登记保存即建);缺失=数据异常,防御 NPE 直接拒(包级:HxAuditService 复用) */
    RpHxProject requireHx(Long projectId) {
        RpHxProject hx = hxMapper.selectById(projectId);
        if (hx == null) {
            throw new BizException("项目扩展信息缺失(rp_hx_project 无行)");
        }
        return hx;
    }

    // ---------- 待签/终版交付下载 ----------
    /** 该流程单最新生效待签件(docx 主交付/pdf 隐藏副产物)流式写响应;不存在抛业务异常 */
    public void writeDeliver(Long uid, String role, Long flowId, String bizType,
                             HttpServletResponse response) throws IOException {
        writeDeliver(uid, role, flowId, bizType, false, response);
    }

    /**
     * 交付下载泛化(N4 Task26 终版):finalVersion=true 查终版件(hx_final_*,
     * 验收确认时渲染,spec §6.2「终版数据 PDF 归档后可看」),仅 status=3 流程单存在——
     * 未通过的单前置拒(终版语义提示),已通过但查不到=未验收确认即无终版。
     * 目录与待签同前缀(终版仅文件名带 final_ 段),故路径定位逻辑复用。
     */
    public void writeDeliver(Long uid, String role, Long flowId, String bizType, boolean finalVersion,
                             HttpServletResponse response) throws IOException {
        RpHxFlow flow = accessibleFlow(uid, role, flowId);
        if (finalVersion && flow.getStatus() != HxFlowStatus.APPROVED) {
            throw new BizException("终版文件在验收确认后生成,该流程单未通过");
        }
        RpAttachment att = attachmentMapper.selectList(new LambdaQueryWrapper<RpAttachment>()
                        .eq(RpAttachment::getProjectId, flow.getProjectId())
                        .eq(RpAttachment::getBizType, bizType)
                        .likeRight(RpAttachment::getFilePath,
                                "hx/" + flow.getProjectId() + "/" + flow.getFlowType() + "/" + flowId + "/")
                        .orderByDesc(RpAttachment::getId)
                        .last("limit 1"))
                .stream().findFirst().orElse(null);
        if (att == null) {
            throw new BizException(finalVersion
                    ? "终版文件不存在(未验收确认或已作废)" : "待签文件不存在(未提交生成或已作废)");
        }
        File f = new File(uploadDir, att.getFilePath());
        if (!f.exists()) {
            throw new BizException((finalVersion ? "终版文件" : "待签文件") + "已缺失:" + att.getFilePath());
        }
        response.setContentType(bizType.endsWith("pdf") ? MediaType.APPLICATION_PDF_VALUE : DOCX_MIME);
        response.setHeader("Content-Disposition", "inline; filename=" + att.getFileName());
        response.setContentLengthLong(f.length());
        try (InputStream in = new FileInputStream(f)) {
            in.transferTo(response.getOutputStream());
        }
    }

    /**
     * 附件内容下载(spec §6.2 教师端 GET /hx/attachment/{id}/download):
     * 仅 hx_scan/hx_apply/hx_extra 白名单——待签渲染件(hx_docx/hx_pdf/hx_report_*)走既有 writeDeliver,
     * 不重复开口;教师按附件→项目做 owner 校验(同 accessibleFlow 防线),admin 直查。
     */
    public void writeAttachment(Long uid, String role, Long attachmentId, HttpServletResponse response) throws IOException {
        RpAttachment att = attachmentMapper.selectById(attachmentId);
        if (att == null) {
            throw new BizException("附件不存在");
        }
        if (!List.of("hx_scan", "hx_apply", "hx_extra").contains(att.getBizType())) {
            throw new BizException("该附件类型不支持直接下载");
        }
        if (!"admin".equals(role)) {
            RpProject p = projectMapper.selectById(att.getProjectId());
            if (p == null || !uid.equals(p.getApplyUserId())) {
                throw new BizException("无权访问该附件");
            }
        }
        File f = new File(uploadDir, att.getFilePath());
        if (!f.exists()) {
            throw new BizException("附件文件已缺失:" + att.getFilePath());
        }
        response.setContentType(contentType(att.getFileName()));
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''"
                + URLEncoder.encode(att.getFileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setContentLengthLong(f.length());
        try (InputStream in = new FileInputStream(f)) {
            in.transferTo(response.getOutputStream());
        }
    }

    /** 附件 Content-Type 按扩展名(扫描件以 pdf/图片为主,兜底二进制流) */
    private static String contentType(String fileName) {
        return switch (ext(fileName)) {
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "docx" -> DOCX_MIME;
            default -> "application/octet-stream";
        };
    }

    // ---------- 通用 ----------
    /** 教师本人流程单(越权防线;消息文本区分 不存在/无权 语义,接口测试统一断言 code!=0)
     *  (包级:FundingService 合同附件上传复用,Task25) */
    RpHxFlow ownedFlow(Long uid, Long flowId) {
        RpHxFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BizException("流程单不存在");
        }
        RpProject p = projectMapper.selectById(flow.getProjectId());
        if (p == null || !uid.equals(p.getApplyUserId())) {
            throw new BizException("无权访问该流程单");
        }
        return flow;
    }

    /** 访问防线(spec §6.4):教师一律 owner 校验(ownedFlow);admin 直查;viewer 不参与横向,按 owner 判定必拒 */
    private RpHxFlow accessibleFlow(Long uid, String role, Long flowId) {
        if ("admin".equals(role)) {
            RpHxFlow flow = flowMapper.selectById(flowId);
            if (flow == null) {
                throw new BizException("流程单不存在");
            }
            return flow;
        }
        return ownedFlow(uid, flowId);   // teacher/viewer 同走归属判定,viewer 名下无项目即"无权访问"
    }

    /** 教师本人项目(越权防线,spec §6.4):不存在/非本人统一拒绝(消息合并,避免探测他人项目存在性) */
    private RpProject ownedProject(Long uid, Long projectId) {
        if (projectId == null) {
            throw new BizException("projectId 必填");
        }
        RpProject p = projectMapper.selectById(projectId);
        if (p == null || !uid.equals(p.getApplyUserId())) {
            throw new BizException("项目不存在或无权访问");
        }
        return p;
    }

    /** change/acceptance 草稿编辑前置:本人+流程类型一致+项目一致+草稿/退回态(Task24 放宽),条件占位防并发提交竞态 */
    private RpHxFlow editableDraftFlow(Long uid, Long flowId, String flowType, Long projectId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (!flowType.equals(flow.getFlowType())) {
            throw new BizException("流程单类型不符");
        }
        if (!editableBeforeApprove(flow.getStatus())) {
            throw new BizException("仅审批通过前可编辑");
        }
        if (!flow.getProjectId().equals(projectId)) {
            throw new BizException("流程单与项目不一致");
        }
        guardEditable(flow.getId(), "仅草稿/退回单可编辑");
        return flow;
    }

    /** 变更/验收提交的确认态强校验(spec §6.1:projectId 必须已登记确认,audit_status=2) */
    private void requireConfirmed(Long projectId, String label) {
        RpProject p = projectMapper.selectById(projectId);
        if (p == null || p.getAuditStatus() == null || p.getAuditStatus() != 2) {
            throw new BizException("项目未确认," + label + "须在登记确认(科技处审核通过)后提交");
        }
    }

    /** 变更前快照(submit 时拍,N4 审批对照依据):project.name/leaderName/deptId + hx.contractAmount/endDate(jackson) */
    private String snapshotBefore(Long projectId) {
        RpProject p = projectMapper.selectById(projectId);
        RpHxProject hx = hxMapper.selectById(projectId);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("name", p.getName());
        snap.put("leaderName", p.getLeaderName());
        snap.put("deptId", p.getDeptId());
        if (hx != null) {
            snap.put("contractAmount", hx.getContractAmount());
            snap.put("endDate", hx.getEndDate());
        }
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException("变更前快照序列化失败:" + e.getMessage());
        }
    }

    /** 到账累计(元):rp_funding_record 实时 SUM(决策#16 不冗余);无记录返回 null(渲染空串)(包级:HxAuditService 复用) */
    BigDecimal receivedTotal(Long projectId) {
        List<RpFundingRecord> rows = fundingMapper.selectList(new LambdaQueryWrapper<RpFundingRecord>()
                .eq(RpFundingRecord::getProjectId, projectId));
        return rows.isEmpty() ? null : rows.stream().map(RpFundingRecord::getAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 条件更新迁移(决策#34-②):影响 0 行=状态已被并发改动,抛错回滚 */
    void transit(Long flowId, int from, int to) {
        int rows = flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId).eq(RpHxFlow::getStatus, from)
                .set(RpHxFlow::getStatus, to));
        if (rows == 0) {
            throw new BizException("状态已变化,请刷新后重试");
        }
    }

    /**
     * 草稿态条件占位校验(决策#34-②,check-then-act 竞态封口):
     * 编辑/删除在 ownedFlow 读到 status=0 后、任何写之前,先按 status=0 条件更新占位,
     * 并发提交(transit 0→1)抢先则此处 0 行即拒。SET 同值(update_time)仍计 matched 行——
     * 依赖 Connector/J 默认 useAffectedRows=false 返回 matched 行数而非 affected,
     * 条件不匹配必返回 0(连接串未带 useAffectedRows=true,勿加,加了同值 SET 会误判 0)。
     */
    private void guardDraft(Long flowId, String rejectMsg) {
        guardStatus(flowId, HxFlowStatus.DRAFT, rejectMsg);
    }

    /** 草稿/退回态条件占位(guardDraft 放宽版,Task24 退回修改重交):编辑允许 status∈{0,4},WHERE IN 验行数 */
    private void guardEditable(Long flowId, String rejectMsg) {
        int rows = flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId)
                .in(RpHxFlow::getStatus, HxFlowStatus.DRAFT, HxFlowStatus.SUBMITTED,
                        HxFlowStatus.SCANNED, HxFlowStatus.REJECTED)
                .set(RpHxFlow::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(rejectMsg);
        }
    }

    /** 指定状态条件占位(guardDraft 泛化:读后写窗口内状态被并发迁移即拒,如回扫重传 vs 审批通过) */
    private void guardStatus(Long flowId, int status, String rejectMsg) {
        int rows = flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId).eq(RpHxFlow::getStatus, status)
                .set(RpHxFlow::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(rejectMsg);
        }
    }

    /** 流程留痕(包级:HxAuditService 审批动作同口径复用) */
    void saveRecord(RpHxFlow flow, String action, String operatorName, Long operatorId, String opinion) {
        RpHxFlowRecord r = new RpHxFlowRecord();
        r.setProjectId(flow.getProjectId());
        r.setFlowId(flow.getId());
        r.setAction(action);
        r.setOperatorId(operatorId);
        r.setOperatorName(operatorName);
        r.setOpinion(opinion);
        r.setCreateBy(operatorName);
        recordMapper.insert(r);
    }

    /** 由库中实体反拼 RegisterSaveDTO(提交时复用必填校验) */
    private RegisterSaveDTO loadRegisterDto(Long projectId) {
        RpProject p = projectMapper.selectById(projectId);
        RpHxProject hx = hxMapper.selectById(projectId);
        RegisterSaveDTO dto = new RegisterSaveDTO();
        dto.setName(p.getName());
        dto.setLeaderName(p.getLeaderName());
        dto.setDeptId(p.getDeptId());
        RegisterSaveDTO.Hx h = new RegisterSaveDTO.Hx();
        if (hx != null) {
            h.setProjectType(hx.getProjectType());
            h.setClientName(hx.getClientName());
            h.setClientNature(hx.getClientNature());
            h.setContractAmount(hx.getContractAmount());
            h.setStartDate(hx.getStartDate() == null ? null : hx.getStartDate().toString());
            h.setEndDate(hx.getEndDate() == null ? null : hx.getEndDate().toString());
        }
        dto.setHx(h);
        return dto;
    }

    /**
     * 成员全量替换:逻辑删旧行+插新行(姓名空行不落库;sortNo 缺省按保存顺序)。
     * members=null(字段未传)=不动成员表(N3 终审挂账,Task28):null 是「本段没交」,
     * 显式空列表才是「清空」——登记/验收编辑路径传 null 不得清掉既有成员行。
     */
    private void replaceMembers(Long projectId, List<MemberDTO> members, String createBy) {
        if (members == null) {
            return;
        }
        memberMapper.delete(new LambdaQueryWrapper<RpMember>().eq(RpMember::getProjectId, projectId));
        int sort = 1;
        for (MemberDTO m : members) {
            if (m == null || isBlank(m.getName())) {
                continue;
            }
            lenCheck(m.getEmployeeNo(), 32, "成员工号");
            lenCheck(m.getName(), 64, "成员姓名");
            lenCheck(m.getUnit(), 255, "成员所在单位");
            lenCheck(m.getTitle(), 32, "成员职称/职务");
            lenCheck(m.getMajor(), 64, "成员专业");
            lenCheck(m.getDuty(), 500, "成员工作任务");
            lenCheck(m.getStudentNo(), 32, "学号");
            lenCheck(m.getMentor(), 64, "指导老师");
            RpMember e = toMemberEntity(m, projectId, createBy);
            if (e.getSortNo() == null) {
                e.setSortNo(sort);
            }
            sort++;
            memberMapper.insert(e);
        }
    }

    /** 预算全量替换:物理清旧行+插新行(uk_project_subject 唯一键与逻辑删互斥,见 RpBudgetMapper) */
    private void replaceBudgets(Long projectId, List<BudgetDTO> budgets, String createBy) {
        budgetMapper.physicalDeleteByProject(projectId);
        if (budgets == null) {
            return;
        }
        for (BudgetDTO b : budgets) {
            // 不受限科目允许金额空(归零落库),其余金额空=不列支跳过
            if (b == null || isBlank(b.getSubject()) || (b.getAmount() == null && !unrestricted(b))) {
                continue;
            }
            budgetMapper.insert(toBudgetEntity(b, projectId, createBy));
        }
    }

    private RpMember toMemberEntity(MemberDTO m, Long projectId, String createBy) {
        RpMember e = new RpMember();
        e.setProjectId(projectId);
        e.setMemberType(m.getMemberType());
        e.setEmployeeNo(m.getEmployeeNo());
        e.setName(m.getName());
        e.setUnit(m.getUnit());
        e.setTitle(m.getTitle());
        e.setMajor(m.getMajor());
        e.setDuty(m.getDuty());
        e.setStudentNo(m.getStudentNo());
        e.setMentor(m.getMentor());
        e.setRatio(m.getRatio());
        e.setAmount(m.getAmount());
        e.setSortNo(m.getSortNo());
        e.setCreateBy(createBy);
        return e;
    }

    private RpBudget toBudgetEntity(BudgetDTO b, Long projectId, String createBy) {
        RpBudget e = new RpBudget();
        e.setProjectId(projectId);
        e.setSubject(b.getSubject());
        // 不受限科目金额归零(规则R2:不计入合计,打印显示口径文字)
        e.setAmount(Boolean.TRUE.equals(unrestricted(b)) ? BigDecimal.ZERO : b.getAmount());
        e.setUnrestricted(Boolean.TRUE.equals(unrestricted(b)) ? 1 : 0);
        e.setCreateBy(createBy);
        return e;
    }

    private static boolean unrestricted(BudgetDTO b) {
        return b.getUnrestricted() != null && b.getUnrestricted() == 1;
    }

    /** DTO 扩展段 → rp_hx_project(日期字符串 yyyy-MM-dd 解析) */
    private void copyHx(RegisterSaveDTO.Hx h, RpHxProject hx) {
        hx.setProjectType(h.getProjectType());
        hx.setClientName(h.getClientName());
        hx.setClientNature(h.getClientNature());
        hx.setContractAmount(h.getContractAmount());
        hx.setStartDate(parseDate(h.getStartDate()));
        hx.setEndDate(parseDate(h.getEndDate()));
        hx.setUseSchoolAsset(h.getUseSchoolAsset());
        hx.setChargeMgmtFee(h.getChargeMgmtFee());
        // mgmtFeeAmount 不入 DTO(比例归科技处,金额由 MgmtFeePolicy 换算)
        hx.setLeaderEmail(h.getLeaderEmail());
        hx.setProjectSummary(h.getProjectSummary());
        hx.setProgressPlan(h.getProgressPlan());
    }

    /** 渲染前填充 RpProject 非表列:deptName(按 dept_id)、leaderPhone(按 apply_user_id)(包级:HxAuditService 复用) */
    void fillTransient(RpProject p) {
        if (p.getDeptId() != null) {
            SysDept d = deptMapper.selectById(p.getDeptId());
            p.setDeptName(d == null ? null : d.getName());
        }
        if (p.getApplyUserId() != null) {
            SysUser u = userMapper.selectById(p.getApplyUserId());
            p.setLeaderPhone(u == null ? null : u.getPhone());
        }
    }

    private String readConfig(String key) {
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key).last("limit 1"));
        return c == null ? "false" : c.getConfigValue();
    }

    /** yyyy-MM-dd 宽松解析(包级:HxAuditService admin 代改复用);非法/null→null */
    static LocalDate parseDate(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new BizException("日期格式应为 yyyy-MM-dd:" + s);
        }
    }

    /** 附件相对路径(相对 upload 根,与 PdfRenderService.toRelative 同口径) */
    private String toRelative(File f) {
        String p = f.getPath().replace('\\', '/');
        String root = new File(uploadDir).getPath().replace('\\', '/');
        return p.startsWith(root + "/") ? p.substring(root.length() + 1) : p;
    }

    /** 附件扩展名(取原名后缀小写,缺失/非法回退空串走白名单拒绝;Task28 起不再兜底 pdf) */
    private static String ext(String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                return filename.substring(dot + 1).toLowerCase();
            }
        }
        return "";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
