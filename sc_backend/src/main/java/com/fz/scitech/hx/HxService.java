package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fz.scitech.common.BizException;
import com.fz.scitech.hx.dto.BudgetDTO;
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
import com.fz.scitech.platform.template.PdfRenderService;
import com.fz.scitech.platform.template.dto.RenderContext;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 横向教师端三流程业务。状态迁移一律条件更新 WHERE status=旧值(决策#34-②),
 * 影响行数 0 抛「状态已变化」防并发双提;教师越权防线=每条查询/更新强制 apply_user_id 归属校验(spec §6.4)。
 * Task20 覆盖登记流程;change/acceptance 由 Task21 扩展。
 */
@Slf4j
@Service
public class HxService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final RpProjectMapper projectMapper;
    private final RpHxProjectMapper hxMapper;
    private final RpHxFlowMapper flowMapper;
    private final RpHxFlowRecordMapper recordMapper;
    private final RpMemberMapper memberMapper;
    private final RpBudgetMapper budgetMapper;
    private final RpAttachmentMapper attachmentMapper;
    private final RpFundingRecordMapper fundingMapper;
    private final RenderModelBuilder modelBuilder;
    private final PdfRenderService pdfRenderService;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysDictMapper dictMapper;
    private final SysConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    @Value("${sc.upload-dir:./upload}")
    private String uploadDir;

    public HxService(RpProjectMapper projectMapper, RpHxProjectMapper hxMapper, RpHxFlowMapper flowMapper,
                     RpHxFlowRecordMapper recordMapper, RpMemberMapper memberMapper, RpBudgetMapper budgetMapper,
                     RpAttachmentMapper attachmentMapper, RpFundingRecordMapper fundingMapper,
                     RenderModelBuilder modelBuilder, PdfRenderService pdfRenderService,
                     SysDeptMapper deptMapper, SysUserMapper userMapper, SysDictMapper dictMapper,
                     SysConfigMapper configMapper, ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.hxMapper = hxMapper;
        this.flowMapper = flowMapper;
        this.recordMapper = recordMapper;
        this.memberMapper = memberMapper;
        this.budgetMapper = budgetMapper;
        this.attachmentMapper = attachmentMapper;
        this.fundingMapper = fundingMapper;
        this.modelBuilder = modelBuilder;
        this.pdfRenderService = pdfRenderService;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.dictMapper = dictMapper;
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 登记草稿 ----------
    /** 幂等:flowId 空新建(kind=2 草稿行+register 草稿流程单),非空整体覆盖(仅 status=0 可编辑) */
    @Transactional
    public Long saveRegisterDraft(Long uid, String username, RegisterSaveDTO dto) {
        validateRegister(dto);
        Long flowId;
        RpProject project;
        if (dto.getFlowId() == null) {
            project = new RpProject();
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
            flowId = flow.getId();
        } else {
            RpHxFlow flow = ownedFlow(uid, dto.getFlowId());
            if (flow.getStatus() != HxFlowStatus.DRAFT) {
                throw new BizException("仅草稿可编辑");
            }
            flowId = flow.getId();
            project = projectMapper.selectById(flow.getProjectId());
            project.setName(dto.getName());
            project.setLeaderName(dto.getLeaderName());
            project.setDeptId(dto.getDeptId());
            projectMapper.updateById(project);
        }
        RpHxProject hx = hxMapper.selectById(project.getId());
        boolean hxNew = hx == null;
        if (hxNew) {
            hx = new RpHxProject();
            hx.setProjectId(project.getId());
            hx.setCreateBy(username);
        }
        copyHx(dto.getHx(), hx);
        if (hxNew) {
            hxMapper.insert(hx);
        } else {
            hxMapper.updateById(hx);
        }
        replaceMembers(project.getId(), dto.getMembers(), username);
        replaceBudgets(project.getId(), dto.getBudgets(), username);
        return flowId;
    }

    /** 必填校验:名称/负责人/院部/类型/委托单位/性质/经费/起止(成员行姓名空则不落库,见 replaceMembers) */
    private void validateRegister(RegisterSaveDTO dto) {
        if (isBlank(dto.getName()) || isBlank(dto.getLeaderName()) || dto.getDeptId() == null) {
            throw new BizException("项目名称/负责人/院部必填");
        }
        RegisterSaveDTO.Hx h = dto.getHx();
        if (h == null || isBlank(h.getProjectType()) || isBlank(h.getClientName())
                || isBlank(h.getClientNature()) || h.getContractAmount() == null
                || isBlank(h.getStartDate()) || isBlank(h.getEndDate())) {
            throw new BizException("项目类型/委托单位/性质/合同经费/起止时间必填");
        }
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
        transit(flowId, HxFlowStatus.DRAFT, HxFlowStatus.SUBMITTED);
        flowMapper.update(null, new LambdaUpdateWrapper<RpHxFlow>()
                .eq(RpHxFlow::getId, flowId).set(RpHxFlow::getSubmitTime, LocalDateTime.now()));
        projectMapper.update(null, new LambdaUpdateWrapper<RpProject>()
                .eq(RpProject::getId, flow.getProjectId())
                .set(RpProject::getAuditStatus, 1)
                .set(RpProject::getDeclareDate, LocalDate.now()));
        renderDeliverables(flow, uid, username);   // 双出 docx+pdf(失败即整体回滚=决策#34-③)
        saveRecord(flow, HxFlowStatus.ACT_SUBMIT, username, uid, null);
    }

    /** 渲染待签交付物:render 双出落盘+附件登记;旧版 hx_docx/hx_pdf 标记作废 */
    private void renderDeliverables(RpHxFlow flow, Long uid, String username) {
        invalidateOld(flow.getId(), flow.getProjectId(), null, "hx_docx", "hx_pdf");
        RpProject p = projectMapper.selectById(flow.getProjectId());
        fillTransient(p);
        RpHxProject hx = hxMapper.selectById(flow.getProjectId());
        Map<String, Object> model;
        String tplType;
        if ("register".equals(flow.getFlowType())) {
            model = modelBuilder.buildRegister(p, hx,
                    memberMapper.selectList(new LambdaQueryWrapper<RpMember>()
                            .eq(RpMember::getProjectId, flow.getProjectId()).orderByAsc(RpMember::getSortNo)),
                    budgetMapper.selectList(new LambdaQueryWrapper<RpBudget>()
                            .eq(RpBudget::getProjectId, flow.getProjectId())));
            tplType = "hx_register";
        } else {
            throw new BizException("未知流程类型:" + flow.getFlowType()); // change/acceptance 由 Task21 扩展
        }
        // operatorId 必传 uid(附件 uploader_id 落库;RenderContext 七参,勿传 null)
        pdfRenderService.render(new RenderContext(
                flow.getId(), flow.getProjectId(), flow.getFlowType(), tplType,
                uid, username, false), model);
    }

    /**
     * 同一流程单旧待签/扫描件作废(逻辑删附件行,文件保留不入列表=设计§3.2)。
     * RpAttachment 无 flowId 列,先按 project+biz_type(+doc_type)收窄再在路径段 "/{flowId}/" 上定位
     * (渲染件与扫描件路径均含该段,斜杠边界保证 flowId=8 不误配 /18/)。
     */
    private void invalidateOld(Long flowId, Long projectId, String docType, String... bizTypes) {
        List<RpAttachment> olds = attachmentMapper.selectList(new LambdaQueryWrapper<RpAttachment>()
                .eq(RpAttachment::getProjectId, projectId)
                .in(RpAttachment::getBizType, Arrays.asList(bizTypes))
                .eq(docType != null, RpAttachment::getDocType, docType)
                .likeRight(RpAttachment::getFilePath, "hx/" + projectId + "/"));
        for (RpAttachment a : olds) {
            if (a.getFilePath().contains("/" + flowId + "/")) {
                attachmentMapper.deleteById(a.getId()); // 逻辑删(deleted=1)
            }
        }
    }

    // ---------- 撤回(三流程共用) ----------
    /** 已提交(1)→草稿(0);已回扫(2)→已提交(1,重传前须先撤回重盖章场景) */
    @Transactional
    public void withdraw(Long uid, Long flowId) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        if (flow.getStatus() == HxFlowStatus.SUBMITTED) {
            transit(flowId, HxFlowStatus.SUBMITTED, HxFlowStatus.DRAFT);
        } else if (flow.getStatus() == HxFlowStatus.SCANNED) {
            transit(flowId, HxFlowStatus.SCANNED, HxFlowStatus.SUBMITTED);
        } else {
            throw new BizException("当前状态不可撤回");
        }
    }

    // ---------- 回扫 ----------
    /**
     * 盖章扫描件上传:status 1→2;已回扫(2)重传=按 docType 覆盖,旧件作废(决策#34-④ 每类仅最新一份生效)。
     * 存盘 upload/hx/{pid}/scan/{flowId}/scan_{ts}.{ext}(目录独立于渲染件)。
     */
    @Transactional
    public Long uploadScan(Long uid, String username, Long flowId, MultipartFile file, String docType) {
        RpHxFlow flow = ownedFlow(uid, flowId);
        int st = flow.getStatus();
        if (st != HxFlowStatus.SUBMITTED && st != HxFlowStatus.SCANNED) {
            throw new BizException("仅已提交状态可上传扫描件");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择扫描件文件");
        }
        validateScanDocType(flow.getFlowType(), docType);
        // 先作废旧件再插新件(新件路径同样含 /{flowId}/ 段,顺序反了会把自己删掉)
        invalidateOld(flowId, flow.getProjectId(), docType, "hx_scan");
        File dir = new File(uploadDir, "hx/" + flow.getProjectId() + "/scan/" + flowId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("扫描件目录创建失败:" + dir);
        }
        File dest = new File(dir, "scan_" + LocalDateTime.now().format(TS) + "." + ext(file.getOriginalFilename()));
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
        if (st == HxFlowStatus.SUBMITTED) {
            transit(flowId, HxFlowStatus.SUBMITTED, HxFlowStatus.SCANNED);
        }
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
        return vo;
    }

    // ---------- 填报分屏预览 ----------
    /**
     * 表单数据直出 docx 预览流(决策#36+直出Word):不落库不落盘,DTO→内存实体→buildRegister→renderDocxStream。
     * Task21 同法扩 change/acceptance 分支。
     */
    public byte[] previewDocx(String flowType, Map<String, Object> data, Long uid) {
        if (flowType == null || data == null) {
            throw new BizException("flowType/data 必填");
        }
        switch (flowType) {
            case "register" -> {
                RegisterSaveDTO dto = objectMapper.convertValue(data, RegisterSaveDTO.class);
                RpProject p = new RpProject();
                p.setApplyUserId(uid);
                p.setName(dto.getName());
                p.setLeaderName(dto.getLeaderName());
                p.setDeptId(dto.getDeptId());
                fillTransient(p);   // deptName/leaderPhone=登记表占位符数据源
                RpHxProject hx = new RpHxProject();
                if (dto.getHx() != null) {
                    copyHx(dto.getHx(), hx);
                }
                List<RpMember> members = new ArrayList<>();
                if (dto.getMembers() != null) {
                    for (MemberDTO m : dto.getMembers()) {
                        if (m == null || isBlank(m.getName())) {
                            continue;
                        }
                        members.add(toMemberEntity(m, null, null));
                    }
                }
                List<RpBudget> budgets = new ArrayList<>();
                if (dto.getBudgets() != null) {
                    for (BudgetDTO b : dto.getBudgets()) {
                        if (b == null || isBlank(b.getSubject()) || b.getAmount() == null) {
                            continue;
                        }
                        budgets.add(toBudgetEntity(b, null, null));
                    }
                }
                return pdfRenderService.renderDocxStream("hx_register",
                        modelBuilder.buildRegister(p, hx, members, budgets));
            }
            default -> throw new BizException("未知流程类型:" + flowType);
        }
    }

    // ---------- 待签交付下载 ----------
    /** 该流程单最新生效待签件(docx 主交付/pdf 隐藏副产物)流式写响应;不存在抛业务异常 */
    public void writeDeliver(Long uid, String role, Long flowId, String bizType,
                             HttpServletResponse response) throws IOException {
        RpHxFlow flow = accessibleFlow(uid, role, flowId);
        RpAttachment att = attachmentMapper.selectList(new LambdaQueryWrapper<RpAttachment>()
                        .eq(RpAttachment::getProjectId, flow.getProjectId())
                        .eq(RpAttachment::getBizType, bizType)
                        .likeRight(RpAttachment::getFilePath,
                                "hx/" + flow.getProjectId() + "/" + flow.getFlowType() + "/" + flowId + "/")
                        .orderByDesc(RpAttachment::getId)
                        .last("limit 1"))
                .stream().findFirst().orElse(null);
        if (att == null) {
            throw new BizException("待签文件不存在(未提交生成或已作废)");
        }
        File f = new File(uploadDir, att.getFilePath());
        if (!f.exists()) {
            throw new BizException("待签文件已缺失:" + att.getFilePath());
        }
        response.setContentType(bizType.endsWith("pdf") ? MediaType.APPLICATION_PDF_VALUE : DOCX_MIME);
        response.setHeader("Content-Disposition", "inline; filename=" + att.getFileName());
        response.setContentLengthLong(f.length());
        try (InputStream in = new FileInputStream(f)) {
            in.transferTo(response.getOutputStream());
        }
    }

    // ---------- 通用 ----------
    /** 教师本人流程单(越权防线;消息文本区分 不存在/无权 语义,接口测试统一断言 code!=0) */
    private RpHxFlow ownedFlow(Long uid, Long flowId) {
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

    /** 教师=ownedFlow 防线;admin/viewer 直查(只读角色按平台口径放行查看) */
    private RpHxFlow accessibleFlow(Long uid, String role, Long flowId) {
        if ("teacher".equals(role)) {
            return ownedFlow(uid, flowId);
        }
        RpHxFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BizException("流程单不存在");
        }
        return flow;
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

    private void saveRecord(RpHxFlow flow, String action, String operatorName, Long operatorId, String opinion) {
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

    /** 成员全量替换:逻辑删旧行+插新行(姓名空行不落库;sortNo 缺省按保存顺序) */
    private void replaceMembers(Long projectId, List<MemberDTO> members, String createBy) {
        memberMapper.delete(new LambdaQueryWrapper<RpMember>().eq(RpMember::getProjectId, projectId));
        if (members == null) {
            return;
        }
        int sort = 1;
        for (MemberDTO m : members) {
            if (m == null || isBlank(m.getName())) {
                continue;
            }
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
            if (b == null || isBlank(b.getSubject()) || b.getAmount() == null) {
                continue;
            }
            budgetMapper.insert(toBudgetEntity(b, projectId, createBy));
        }
    }

    private RpMember toMemberEntity(MemberDTO m, Long projectId, String createBy) {
        RpMember e = new RpMember();
        e.setProjectId(projectId);
        e.setMemberType(m.getMemberType());
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
        e.setAmount(b.getAmount());
        e.setCreateBy(createBy);
        return e;
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
        hx.setMgmtFeeAmount(h.getMgmtFeeAmount());
        hx.setLeaderEmail(h.getLeaderEmail());
        hx.setProjectSummary(h.getProjectSummary());
        hx.setProgressPlan(h.getProgressPlan());
    }

    /** 渲染前填充 RpProject 非表列:deptName(按 dept_id)、leaderPhone(按 apply_user_id) */
    private void fillTransient(RpProject p) {
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

    private static LocalDate parseDate(String s) {
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

    /** 扫描件扩展名(取原名后缀,非法/缺失回退 pdf) */
    private static String ext(String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                String e = filename.substring(dot + 1).toLowerCase();
                if (e.matches("[a-z0-9]{1,8}")) {
                    return e;
                }
            }
        }
        return "pdf";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
