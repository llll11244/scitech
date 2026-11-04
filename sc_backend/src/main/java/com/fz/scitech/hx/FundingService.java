package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fz.scitech.common.BizException;
import com.fz.scitech.hx.dto.FundingBody;
import com.fz.scitech.hx.entity.RpFundingRecord;
import com.fz.scitech.hx.entity.RpHxFlow;
import com.fz.scitech.hx.entity.RpHxProject;
import com.fz.scitech.hx.entity.RpProject;
import com.fz.scitech.hx.mapper.RpFundingRecordMapper;
import com.fz.scitech.hx.mapper.RpHxFlowMapper;
import com.fz.scitech.hx.mapper.RpHxProjectMapper;
import com.fz.scitech.hx.mapper.RpProjectMapper;
import com.fz.scitech.platform.attachment.RpAttachment;
import com.fz.scitech.platform.attachment.mapper.RpAttachmentMapper;
import com.fz.scitech.system.entity.SysDept;
import com.fz.scitech.system.entity.SysNotify;
import com.fz.scitech.system.mapper.SysDeptMapper;
import com.fz.scitech.system.mapper.SysNotifyMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 到账管理+横向项目列表+合同附件(Task25,spec §4.8/§6.3/决策#16/#21):
 * 到账仅 admin 增删查(源头=计财处/银行流水),累计实时 SUM 不回写(单一事实源),
 * 录入/删除均发教师通知;与项目状态完全解耦(结题后仍可补录尾款,决策#17 金额三层);
 * 教师只读=apply_user_id 强制(spec §6.4)。
 * 与 HxService/HxAuditService 同包,复用 ownedFlow/receivedTotal 防线与口径,不复制逻辑
 * (N4 计划架构约定:守住 HxService 千行线,新业务独立成 Service)。
 */
@Service
public class FundingService {

    /** 附件文件名时间戳:毫秒级(Task25 Minor⑤ 收口,Task28)——同秒重传同 docType 会物理覆盖旧件 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    /** 合同附件允许扩展名(uploadScan 已于 Task28 同口径收紧:见 HxService.SCAN_EXTS) */
    private static final List<String> ATTACH_EXTS = List.of("pdf", "png", "jpg", "jpeg");
    /** 通用附件端点当前支持的文档类型(N4 计划 ruling:合同扫描件;后续扩展在此加) */
    private static final List<String> ATTACH_DOC_TYPES = List.of("合同");

    private final RpProjectMapper projectMapper;
    private final RpHxProjectMapper hxMapper;
    private final RpHxFlowMapper flowMapper;
    private final RpAttachmentMapper attachmentMapper;
    private final RpFundingRecordMapper fundingMapper;
    private final SysDeptMapper deptMapper;
    private final SysNotifyMapper notifyMapper;
    private final HxService hxService;

    @Value("${sc.upload-dir:./upload}")
    private String uploadDir;

    public FundingService(RpProjectMapper projectMapper, RpHxProjectMapper hxMapper,
                          RpHxFlowMapper flowMapper, RpAttachmentMapper attachmentMapper,
                          RpFundingRecordMapper fundingMapper, SysDeptMapper deptMapper,
                          SysNotifyMapper notifyMapper, HxService hxService) {
        this.projectMapper = projectMapper;
        this.hxMapper = hxMapper;
        this.flowMapper = flowMapper;
        this.attachmentMapper = attachmentMapper;
        this.fundingMapper = fundingMapper;
        this.deptMapper = deptMapper;
        this.notifyMapper = notifyMapper;
        this.hxService = hxService;
    }

    // ---------- 到账明细(admin,spec §4.8) ----------
    /** 录入一笔到账:校验→插入→教师通知「您的项目《X》到账 XX 元」;与项目状态解耦,结题后仍可补录 */
    @Transactional
    public Long addFunding(Long adminId, String adminName, FundingBody body) {
        if (body == null || body.projectId() == null) {
            throw new BizException("projectId 必填");
        }
        RpProject p = projectMapper.selectById(body.projectId());
        if (p == null || p.getKind() == null || p.getKind() != 2) {
            throw new BizException("横向项目不存在");
        }
        if (body.amount() == null) {
            throw new BizException("到账金额必须大于 0");
        }
        // 两位小数收口(2026-11-05 小项①:先舍入再判正,防 0.001 落库变 0.00;通知文案与落库同值)
        BigDecimal amount = body.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new BizException("到账金额必须大于 0");
        }
        HxService.amountCheck(amount, "到账金额");
        LocalDate receivedDate = parseDate(body.receivedDate());
        RpFundingRecord r = new RpFundingRecord();
        r.setProjectId(p.getId());
        r.setAmount(amount);
        r.setReceivedDate(receivedDate);
        r.setPayer(trimToNull(body.payer()));
        r.setBankFlowNo(trimToNull(body.bankFlowNo()));
        r.setRemark(trimToNull(body.remark()));
        r.setCreateBy(adminName);
        fundingMapper.insert(r);
        notifyTeacher(p.getApplyUserId(), "横向项目到账通知",
                "您的项目《" + p.getName() + "》到账 " + amount + " 元(到账日期:" + receivedDate
                        + "),当前累计到账:" + hxService.receivedTotal(p.getId()) + " 元。",
                p.getId(), adminName);
        return r.getId();
    }

    /** 删到账(逻辑删):教师核销通知「您的项目《X》核销 XX 元」;累计实时 SUM 自动回落,不回写 */
    @Transactional
    public void deleteFunding(Long adminId, String adminName, Long recordId) {
        RpFundingRecord r = fundingMapper.selectById(recordId);
        if (r == null) {
            throw new BizException("到账记录不存在");
        }
        RpProject p = projectMapper.selectById(r.getProjectId());
        fundingMapper.deleteById(recordId);   // 逻辑删(yml 全局 deleted 字段)
        if (p != null) {
            notifyTeacher(p.getApplyUserId(), "横向项目到账核销通知",
                    "您的项目《" + p.getName() + "》核销到账 " + r.getAmount()
                            + " 元(科技处删除该笔记录),当前累计到账:"
                            + hxService.receivedTotal(p.getId()) + " 元。",
                    p.getId(), adminName);
        }
    }

    /** 到账明细+累计(按到账日期/序号升序=台账口径);admin 按项目查与教师只读共用 */
    public Map<String, Object> fundingList(Long projectId) {
        if (projectId == null) {
            throw new BizException("projectId 必填");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("records", fundingMapper.selectList(new LambdaQueryWrapper<RpFundingRecord>()
                .eq(RpFundingRecord::getProjectId, projectId)
                .orderByAsc(RpFundingRecord::getReceivedDate)
                .orderByAsc(RpFundingRecord::getId)));
        m.put("total", hxService.receivedTotal(projectId));
        return m;
    }

    /** 教师到账只读(spec §6.1 项目详情-到账):apply_user_id 强制(spec §6.4);消息合并防探测 */
    public Map<String, Object> teacherFunding(Long uid, Long projectId) {
        RpProject p = projectMapper.selectById(projectId);
        if (p == null || !uid.equals(p.getApplyUserId())) {
            throw new BizException("项目不存在或无权访问");
        }
        return fundingList(projectId);
    }

    // ---------- 横向项目列表(admin,spec §6.3/决策#21) ----------
    /**
     * 全部横向项目(kind=2)聚合:编号/名称/负责人/院部/委托单位/合同经费/到账累计(实时 SUM)/
     * 执行状态/登记日期+在途流程单概要,按 id 倒序;批量查三次库聚合(校规模量小不分页)。
     * 执行状态推导:已结题(status=结题) > 变更中(有在途变更单 1/2=审批管道中)
     * > 在研(已确认 audit_status=2) > 登记中(未确认,含草稿/待审/退回——未入台账不宜称在研)。
     */
    public List<Map<String, Object>> projectList() {
        List<RpProject> projects = projectMapper.selectList(new LambdaQueryWrapper<RpProject>()
                .eq(RpProject::getKind, 2).orderByDesc(RpProject::getId));
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> ids = projects.stream().map(RpProject::getId).collect(Collectors.toList());
        Map<Long, RpHxProject> hxByPid = hxMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(RpHxProject::getProjectId, h -> h));
        // 到账累计:一次查全量明细按项目分组求和(实时 SUM 单一事实源,决策#16)
        Map<Long, BigDecimal> totalByPid = fundingMapper.selectList(new LambdaQueryWrapper<RpFundingRecord>()
                        .in(RpFundingRecord::getProjectId, ids))
                .stream().filter(r -> r.getAmount() != null)
                .collect(Collectors.groupingBy(RpFundingRecord::getProjectId,
                        Collectors.reducing(BigDecimal.ZERO, RpFundingRecord::getAmount, BigDecimal::add)));
        Map<Long, List<RpHxFlow>> flowsByPid = flowMapper.selectList(new LambdaQueryWrapper<RpHxFlow>()
                        .in(RpHxFlow::getProjectId, ids))
                .stream().collect(Collectors.groupingBy(RpHxFlow::getProjectId));
        List<Long> deptIds = projects.stream().map(RpProject::getDeptId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RpProject p : projects) {
            RpHxProject hx = hxByPid.get(p.getId());
            List<RpHxFlow> flows = flowsByPid.getOrDefault(p.getId(), List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", p.getId());
            row.put("approvalNo", p.getApprovalNo());
            row.put("name", p.getName());
            row.put("leaderName", p.getLeaderName());
            row.put("deptName", p.getDeptId() == null ? null : deptNames.get(p.getDeptId()));
            row.put("clientName", hx == null ? null : hx.getClientName());
            row.put("contractAmount", hx == null ? null : hx.getContractAmount());
            row.put("receivedTotal", totalByPid.get(p.getId()));
            row.put("execStatus", execStatus(p, flows));
            row.put("status", p.getStatus());
            row.put("auditStatus", p.getAuditStatus());
            row.put("hxRegisterDate", hx == null ? null : hx.getHxRegisterDate());
            row.put("finishDate", p.getFinishDate());
            // 在途流程单概要(1/2=审批管道中):供列表「待办」提示
            List<Map<String, Object>> pending = new ArrayList<>();
            for (RpHxFlow f : flows) {
                if (f.getStatus() != null && (f.getStatus() == HxFlowStatus.SUBMITTED
                        || f.getStatus() == HxFlowStatus.SCANNED)) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("flowId", f.getId());
                    pm.put("flowType", f.getFlowType());
                    pm.put("status", f.getStatus());
                    pm.put("submitTime", f.getSubmitTime());
                    pending.add(pm);
                }
            }
            row.put("pendingFlows", pending);
            rows.add(row);
        }
        return rows;
    }

    /** 执行状态推导(见 projectList 注释口径) */
    private static String execStatus(RpProject p, List<RpHxFlow> flows) {
        if ("结题".equals(p.getStatus())) {
            return "已结题";
        }
        boolean changing = flows.stream().anyMatch(f -> "change".equals(f.getFlowType())
                && f.getStatus() != null
                && (f.getStatus() == HxFlowStatus.SUBMITTED || f.getStatus() == HxFlowStatus.SCANNED));
        if (changing) {
            return "变更中";
        }
        return p.getAuditStatus() != null && p.getAuditStatus() == 2 ? "在研" : "登记中";
    }

    // ---------- 合同附件(教师,N4 计划 ruling) ----------
    /**
     * 合同扫描件上传(通用附件,biz_type=hx_apply,doc_type=合同):
     * 本人流程单任何状态可传(不触发状态迁移、不留流程痕——非流程动作);
     * 同 flowId 同 docType 重传覆盖(旧件逻辑删,与 uploadScan 每类最新一份生效同口径);
     * 存盘 upload/hx/{pid}/apply/{flowId}/apply_{ts}.{ext}。
     */
    @Transactional
    public Long uploadApply(Long uid, String username, Long flowId, String docType, MultipartFile file) {
        RpHxFlow flow = hxService.ownedFlow(uid, flowId);
        if (docType == null || !ATTACH_DOC_TYPES.contains(docType)) {
            throw new BizException("附件类型不合法,当前支持:" + String.join("/", ATTACH_DOC_TYPES));
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择附件文件");
        }
        String ext = ext(file.getOriginalFilename());
        if (!ATTACH_EXTS.contains(ext)) {
            throw new BizException("附件格式仅支持:" + String.join("/", ATTACH_EXTS));
        }
        Long pid = flow.getProjectId();
        // 旧件作废:同项目+biz+docType+完整前缀 hx/{pid}/apply/{fid}/(与 HxService.invalidateOld 同口径)
        attachmentMapper.delete(new LambdaQueryWrapper<RpAttachment>()
                .eq(RpAttachment::getProjectId, pid)
                .eq(RpAttachment::getBizType, "hx_apply")
                .eq(RpAttachment::getDocType, docType)
                .likeRight(RpAttachment::getFilePath, "hx/" + pid + "/apply/" + flowId + "/"));
        File dir = new File(uploadDir, "hx/" + pid + "/apply/" + flowId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("附件目录创建失败:" + dir);
        }
        File dest = new File(dir, "apply_" + LocalDateTime.now().format(TS) + "." + ext);
        try (InputStream in = file.getInputStream()) {
            java.nio.file.Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException("附件保存失败:" + e.getMessage());
        }
        RpAttachment att = new RpAttachment();
        att.setProjectId(pid);
        att.setBizType("hx_apply");
        att.setDocType(docType);
        att.setFileName(file.getOriginalFilename());
        att.setFilePath(toRelative(dest));
        att.setFileSize(dest.length());
        att.setUploaderId(uid);
        att.setCreateBy(username);
        attachmentMapper.insert(att);
        return att.getId();
    }

    // ---------- 通用 ----------
    /** 教师通知(SysNotify 直插,isRead=0;与 HxAuditService 同口径;到账类通知无流程单,flowId=null) */
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

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            throw new BizException("到账日期必填(yyyy-MM-dd)");
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new BizException("到账日期格式应为 yyyy-MM-dd:" + s);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 附件相对路径(相对 upload 根;与 HxService.toRelative 同口径,FundingService 自持 uploadDir) */
    private String toRelative(File f) {
        String p = f.getPath().replace('\\', '/');
        String root = new File(uploadDir).getPath().replace('\\', '/');
        return p.startsWith(root + "/") ? p.substring(root.length() + 1) : p;
    }

    /** 附件扩展名(取原名后缀小写;与 uploadScan 不同——此处白名单校验,缺失即空串走拒绝分支) */
    private static String ext(String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                return filename.substring(dot + 1).toLowerCase();
            }
        }
        return "";
    }
}
