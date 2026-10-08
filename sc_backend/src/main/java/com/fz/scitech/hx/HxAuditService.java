package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fz.scitech.common.BizException;
import com.fz.scitech.hx.entity.RpHxFlow;
import com.fz.scitech.hx.entity.RpHxProject;
import com.fz.scitech.hx.entity.RpMember;
import com.fz.scitech.hx.entity.RpProject;
import com.fz.scitech.hx.mapper.RpHxFlowMapper;
import com.fz.scitech.hx.mapper.RpHxProjectMapper;
import com.fz.scitech.hx.mapper.RpMemberMapper;
import com.fz.scitech.hx.mapper.RpProjectMapper;
import com.fz.scitech.platform.template.PdfRenderService;
import com.fz.scitech.platform.template.dto.RenderContext;
import com.fz.scitech.system.entity.SysDept;
import com.fz.scitech.system.entity.SysNotify;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysDeptMapper;
import com.fz.scitech.system.mapper.SysNotifyMapper;
import com.fz.scitech.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 科技处审批业务(Task24,spec §3.3 副作用权威定义):
 * 通过=transit 2→3 + 各流程副作用(登记入台账编号/变更按 change_type 改写/验收结题+终版两张渲染)
 * +教师通知 +留痕,与状态迁移同事务(渲染失败整体回滚=决策#34-③);
 * 退回=status 1/2 均可(1=盖章前预检,决策#18),意见必填,→4;
 * 教师重交(resubmit)在 HxService(教师端入口)。
 * 与教师端 HxService 分立(守住千行线,N4 计划架构约定);同包复用 transit/saveRecord/
 * requireHx/fillTransient/receivedTotal,不复制逻辑。
 */
@Slf4j
@Service
public class HxAuditService {

    /** 终版完成时间格式(与样例口径一致,如 2027年12月20日) */
    private static final DateTimeFormatter CN_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");

    private final RpProjectMapper projectMapper;
    private final RpHxProjectMapper hxMapper;
    private final RpHxFlowMapper flowMapper;
    private final RpMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysNotifyMapper notifyMapper;
    private final RenderModelBuilder modelBuilder;
    private final PdfRenderService pdfRenderService;
    private final HxService hxService;

    public HxAuditService(RpProjectMapper projectMapper, RpHxProjectMapper hxMapper, RpHxFlowMapper flowMapper,
                          RpMemberMapper memberMapper, SysUserMapper userMapper, SysDeptMapper deptMapper,
                          SysNotifyMapper notifyMapper, RenderModelBuilder modelBuilder,
                          PdfRenderService pdfRenderService, HxService hxService) {
        this.projectMapper = projectMapper;
        this.hxMapper = hxMapper;
        this.flowMapper = flowMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.notifyMapper = notifyMapper;
        this.modelBuilder = modelBuilder;
        this.pdfRenderService = pdfRenderService;
        this.hxService = hxService;
    }

    // ---------- 审批列表 ----------
    /**
     * 审批列表(admin,spec §6.3):flow+project+负责人/院部聚合,全量按提交时间倒序
     * (草稿无 submit_time 自然沉底),flowType/status 可选过滤;量小不做分页。
     */
    public List<Map<String, Object>> auditList(String flowType, Integer status) {
        List<RpHxFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<RpHxFlow>()
                .eq(flowType != null && !flowType.isBlank(), RpHxFlow::getFlowType, flowType)
                .eq(status != null, RpHxFlow::getStatus, status)
                .orderByDesc(RpHxFlow::getSubmitTime)
                .orderByDesc(RpHxFlow::getId));
        if (flows.isEmpty()) {
            return List.of();
        }
        Map<Long, RpProject> projects = projectMapper.selectBatchIds(
                        flows.stream().map(RpHxFlow::getProjectId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(RpProject::getId, p -> p));
        List<Long> deptIds = projects.values().stream()
                .map(RpProject::getDeptId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RpHxFlow f : flows) {
            RpProject p = projects.get(f.getProjectId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("flowId", f.getId());
            row.put("projectId", f.getProjectId());
            row.put("flowType", f.getFlowType());
            row.put("status", f.getStatus());
            row.put("changeType", f.getChangeType());
            row.put("submitTime", f.getSubmitTime());
            row.put("scanTime", f.getScanTime());
            row.put("confirmTime", f.getConfirmTime());
            if (p != null) {
                row.put("name", p.getName());
                row.put("leaderName", p.getLeaderName());
                row.put("deptName", p.getDeptId() == null ? null : deptNames.get(p.getDeptId()));
                row.put("approvalNo", p.getApprovalNo());
                row.put("auditStatus", p.getAuditStatus());
            }
            rows.add(row);
        }
        return rows;
    }

    // ---------- 通过(spec §3.3 三流程副作用) ----------
    /** 通过:仅已回扫(2)单可审;验收 body.verifiedAmount 必填;副作用与迁移同事务 */
    @Transactional
    public void review(Long adminId, String adminName, Long flowId, BigDecimal verifiedAmount) {
        RpHxFlow flow = requireFlow(flowId);
        if (flow.getStatus() != HxFlowStatus.SCANNED) {
            throw new BizException("仅已回扫(待审核)单可审批");
        }
        switch (flow.getFlowType()) {
            case "register" -> approveRegister(adminId, adminName, flow);
            case "change" -> approveChange(adminId, adminName, flow);
            case "acceptance" -> approveAcceptance(adminId, adminName, flow, verifiedAmount);
            default -> throw new BizException("未知流程类型:" + flow.getFlowType());
        }
    }

    /** 登记通过:入台账(audit_status=2/立项/编号)+确认日+通知(spec §3.3) */
    private void approveRegister(Long adminId, String adminName, RpHxFlow flow) {
        Long pid = flow.getProjectId();
        RpProject p = requireProject(pid);
        transitAndConfirm(flow);
        // 编号 HX{年}-{序}:序=当年 max+1;uk_approval_no 兜底并发,冲突重试一次
        String approvalNo = allotApprovalNo(pid);
        hxMapper.update(null, new LambdaUpdateWrapper<RpHxProject>()
                .eq(RpHxProject::getProjectId, pid)
                .set(RpHxProject::getHxRegisterDate, LocalDate.now()));
        notifyTeacher(p.getApplyUserId(), "横向项目登记确认通过",
                "您的横向项目《" + p.getName() + "》已通过登记确认,项目编号:" + approvalNo
                        + ",已正式入台账(在研)。", pid, adminName);
        hxService.saveRecord(flow, HxFlowStatus.ACT_APPROVE, adminName, adminId, null);
    }

    /** 变更通过:按 change_type 改写生效(before_json 对照不拦截,N4 审批界面展示 before/after);台账状态不变 */
    private void approveChange(Long adminId, String adminName, RpHxFlow flow) {
        Long pid = flow.getProjectId();
        RpProject p = requireProject(pid);
        transitAndConfirm(flow);
        List<String> types = List.of(RenderModelBuilder.splitTrim(flow.getChangeType()));
        boolean leaderChanged = false;
        if (types.contains("项目负责人")) {
            SysUser u = userMapper.selectById(flow.getNewLeaderId());
            if (u == null) {
                throw new BizException("变更后负责人账号不存在");
            }
            projectMapper.update(null, new LambdaUpdateWrapper<RpProject>()
                    .eq(RpProject::getId, pid)
                    .set(RpProject::getApplyUserId, u.getId())
                    .set(RpProject::getLeaderName, u.getRealName()));
            leaderChanged = !u.getId().equals(p.getApplyUserId());
        }
        LambdaUpdateWrapper<RpHxProject> hu = new LambdaUpdateWrapper<RpHxProject>()
                .eq(RpHxProject::getProjectId, pid);
        boolean hxChanged = false;
        if (types.contains("项目经费")) {
            hu.set(RpHxProject::getContractAmount, flow.getNewAmount());
            hxChanged = true;
        }
        if (types.contains("执行周期")) {
            hu.set(RpHxProject::getEndDate, flow.getNewEndDate());
            hxChanged = true;
        }
        if (hxChanged) {
            hxMapper.update(null, hu);
        }
        String content = "您的项目《" + p.getName() + "》变更单已通过审核并生效(变更类型:"
                + flow.getChangeType() + "),相关字段已按变更内容更新。";
        // 负责人已变更时项目归属同步迁移:原负责人与新负责人都通知(原负责人需知晓项目已移交)
        notifyTeacher(p.getApplyUserId(), "横向项目变更生效", content, pid, adminName);
        if (leaderChanged) {
            notifyTeacher(flow.getNewLeaderId(), "横向项目变更生效",
                    "项目《" + p.getName() + "》负责人已变更为您,请关注后续流程。", pid, adminName);
        }
        hxService.saveRecord(flow, HxFlowStatus.ACT_APPROVE, adminName, adminId, null);
    }

    /** 验收通过:结题+核定额+终版两张渲染(hx_acceptance+hx_report,finalVersion=true)+通知 */
    private void approveAcceptance(Long adminId, String adminName, RpHxFlow flow, BigDecimal verifiedAmount) {
        if (verifiedAmount == null) {
            throw new BizException("核定到账金额(verifiedAmount)必填");
        }
        // 量级收口到分(DECIMAL(14,2) 同标度,四舍五入)后再校验:负数/0 拒,
        // 与 FundingService.addFunding 到账金额同口径(核定额入台账并渲进终版归档件,须先拦)
        verifiedAmount = verifiedAmount.setScale(2, RoundingMode.HALF_UP);
        if (verifiedAmount.signum() <= 0) {
            throw new BizException("核定到账金额必须大于 0");
        }
        Long pid = flow.getProjectId();
        RpProject p = requireProject(pid);
        LocalDate today = LocalDate.now();
        transitAndConfirm(flow);
        projectMapper.update(null, new LambdaUpdateWrapper<RpProject>()
                .eq(RpProject::getId, pid)
                .set(RpProject::getStatus, "结题")
                .set(RpProject::getFinishDate, today));
        hxMapper.update(null, new LambdaUpdateWrapper<RpHxProject>()
                .eq(RpHxProject::getProjectId, pid)
                .set(RpHxProject::getVerifiedAmount, verifiedAmount));
        renderFinalDeliverables(flow, adminId, adminName, verifiedAmount, today);
        notifyTeacher(p.getApplyUserId(), "横向项目验收通过",
                "您的项目《" + p.getName() + "》已通过验收确认,核定到账金额:" + verifiedAmount
                        + " 元,项目已结题,终版文件已归档。", pid, adminName);
        hxService.saveRecord(flow, HxFlowStatus.ACT_VERIFY, adminName, adminId,
                "核定到账金额:" + verifiedAmount + " 元");
    }

    /**
     * 终版两张渲染(仿 HxService.renderDeliverables 结构,finalVersion=true,不动该方法):
     * hx_acceptance+hx_report,stem 分 tplType→hx_final_docx/hx_final_pdf 与
     * hx_final_report_docx/hx_final_report_pdf(N3 终审注意②);
     * 终版补渲染 buildAcceptance 留空的完成时间/核定到账(万元)。
     */
    private void renderFinalDeliverables(RpHxFlow flow, Long uid, String username,
                                         BigDecimal verifiedAmount, LocalDate finishDate) {
        RpProject p = projectMapper.selectById(flow.getProjectId());
        hxService.fillTransient(p);
        RpHxProject hx = hxService.requireHx(flow.getProjectId());
        List<RpMember> members = memberMapper.selectList(new LambdaQueryWrapper<RpMember>()
                .eq(RpMember::getProjectId, flow.getProjectId()).orderByAsc(RpMember::getSortNo));
        Map<String, Object> acc = modelBuilder.buildAcceptance(p, hx, flow, members,
                hxService.receivedTotal(flow.getProjectId()));
        acc.put("finishDate", CN_DATE.format(finishDate));                       // 终版补:完成时间=确认日
        acc.put("verifiedAmountWan", RenderModelBuilder.wan(verifiedAmount));    // 终版补:核定到账(万元)
        pdfRenderService.render(new RenderContext(flow.getId(), flow.getProjectId(),
                flow.getFlowType(), "hx_acceptance", uid, username, true), acc);
        pdfRenderService.render(new RenderContext(flow.getId(), flow.getProjectId(),
                flow.getFlowType(), "hx_report", uid, username, true),
                modelBuilder.buildReport(p, hx, flow));
    }

    // ---------- 退回(spec §3.2/决策#18) ----------
    /** 退回:status 1/2 均可(1=盖章前预检),意见必填,→4;教师通知以便修改重交 */
    @Transactional
    public void reject(Long adminId, String adminName, Long flowId, String opinion, boolean isPrecheck) {
        if (opinion == null || opinion.isBlank()) {
            throw new BizException("退回意见必填");
        }
        RpHxFlow flow = requireFlow(flowId);
        int st = flow.getStatus();
        if (st != HxFlowStatus.SUBMITTED && st != HxFlowStatus.SCANNED) {
            throw new BizException("仅已提交/已回扫单可退回");
        }
        hxService.transit(flowId, st, HxFlowStatus.REJECTED);
        RpProject p = requireProject(flow.getProjectId());
        notifyTeacher(p.getApplyUserId(), "横向" + flowLabel(flow.getFlowType()) + "单被退回",
                "您的" + flowLabel(flow.getFlowType()) + "单(项目《" + p.getName() + "》)已被科技处退回,"
                        + "请修改后重新提交(重交后原盖章件作废,需重新盖章回扫)。退回意见:" + opinion,
                p.getId(), adminName);
        hxService.saveRecord(flow, isPrecheck ? HxFlowStatus.ACT_PRECHECK_REJECT : HxFlowStatus.ACT_REJECT,
                adminName, adminId, opinion);
    }

    // ---------- 通用 ----------
    /** 2→3 条件迁移+确认时间(先迁移占住状态,后续副作用失败整体回滚,决策#34-②③) */
    private void transitAndConfirm(RpHxFlow flow) {
        hxService.transit(flow.getId(), HxFlowStatus.SCANNED, HxFlowStatus.APPROVED);
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flow.getId())
                .set(RpHxFlow::getConfirmTime, LocalDateTime.now()));
    }

    /** 项目编号分配:HX{年}-{序}(序=当年 max 解析尾号+1,3 位起补零);唯一索引冲突重试一次 */
    private String allotApprovalNo(Long projectId) {
        String prefix = "HX" + LocalDate.now().getYear() + "-";
        for (int attempt = 0; attempt < 2; attempt++) {
            String no = prefix + String.format("%03d", nextSeq(prefix));
            try {
                projectMapper.update(null, new LambdaUpdateWrapper<RpProject>()
                        .eq(RpProject::getId, projectId)
                        .set(RpProject::getApprovalNo, no)
                        .set(RpProject::getAuditStatus, 2)
                        .set(RpProject::getStatus, "立项"));
                return no;
            } catch (DuplicateKeyException e) {
                log.warn("项目编号 {} 冲突(uk_approval_no),第 {} 次重算", no, attempt + 1);
            }
        }
        throw new BizException("项目编号生成冲突,请重试");
    }

    /** 当年已用最大序号+1(approval_no like 'HX{yyyy}-%' 取最大解析尾号;等宽补零下字典序=数值序) */
    private int nextSeq(String prefix) {
        RpProject max = projectMapper.selectOne(new LambdaQueryWrapper<RpProject>()
                .likeRight(RpProject::getApprovalNo, prefix)
                .orderByDesc(RpProject::getApprovalNo)
                .last("limit 1"));
        if (max == null || max.getApprovalNo() == null) {
            return 1;
        }
        try {
            return Integer.parseInt(max.getApprovalNo().substring(prefix.length())) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private RpHxFlow requireFlow(Long flowId) {
        RpHxFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BizException("流程单不存在");
        }
        return flow;
    }

    private RpProject requireProject(Long projectId) {
        RpProject p = projectMapper.selectById(projectId);
        if (p == null) {
            throw new BizException("项目不存在");
        }
        return p;
    }

    /** 教师通知(SysNotify 直插,isRead=0) */
    private void notifyTeacher(Long userId, String title, String content, Long projectId, String createBy) {
        if (userId == null) {
            return;
        }
        SysNotify n = new SysNotify();
        n.setUserId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setProjectId(projectId);
        n.setIsRead(0);
        n.setCreateBy(createBy);
        notifyMapper.insert(n);
    }

    /** 流程类型中文标签 */
    private static String flowLabel(String flowType) {
        return switch (flowType) {
            case "register" -> "登记";
            case "change" -> "变更";
            case "acceptance" -> "验收";
            default -> flowType;
        };
    }
}
