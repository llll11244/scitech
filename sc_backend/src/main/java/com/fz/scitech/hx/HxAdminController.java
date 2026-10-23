package com.fz.scitech.hx;

import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.hx.dto.FundingBody;
import com.fz.scitech.hx.dto.RejectBody;
import com.fz.scitech.hx.dto.ReviewBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 科技处端横向接口(Task24 审批;Task25 到账/项目列表,spec §6.3),全部仅 admin(spec §6.4;
 * viewer/teacher 拒)。教师重交/到账只读/合同附件入口在 HxController(教师本人操作)。
 */
@RestController
@RequestMapping("/hx")
public class HxAdminController {

    private final HxAuditService auditService;
    private final FundingService fundingService;

    public HxAdminController(HxAuditService auditService, FundingService fundingService) {
        this.auditService = auditService;
        this.fundingService = fundingService;
    }

    /** 审批列表:flowType/status 可选过滤,聚合项目/负责人/院部,提交时间倒序(量小不分页) */
    @GetMapping("/audit")
    public Result<List<Map<String, Object>>> audit(@RequestParam(required = false) String flowType,
                                                   @RequestParam(required = false) Integer status,
                                                   HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(auditService.auditList(flowType, status));
    }

    /** 通过:登记/变更/验收各自副作用(spec §3.3);验收 body.verifiedAmount(核定到账,元)必填 */
    @PostMapping("/{flowId}/review")
    public Result<Void> review(@PathVariable Long flowId, @RequestBody(required = false) ReviewBody body,
                               HttpServletRequest request) {
        requireAdmin(request);
        auditService.review(uid(request), username(request), flowId,
                body == null ? null : body.verifiedAmount());
        return Result.ok(null);
    }

    /**
     * 预审通过(2026-10-23 预审流转):仅 status=1,1→5;通知教师打印盖章后上传扫描件。
     * 预审通过前教师不可上传盖章扫描件(HxService.uploadScan 门槛)。
     */
    @PostMapping("/{flowId}/precheck")
    public Result<Void> precheck(@PathVariable Long flowId, HttpServletRequest request) {
        requireAdmin(request);
        auditService.precheckPass(uid(request), username(request), flowId);
        return Result.ok(null);
    }

    /**
     * admin 修正教师填报数据(HTML转正 决策③):仅 status∈{1,2},任意表单字段;
     * body=对应流程保存 DTO 同构 JSON(与教师保存接口同结构);留痕 admin_edit+通知教师。
     */
    @PostMapping("/{flowId}/admin-save")
    public Result<Void> adminSave(@PathVariable Long flowId, @RequestBody Map<String, Object> body,
                                  HttpServletRequest request) {
        requireAdmin(request);
        auditService.adminSave(uid(request), username(request), flowId, body);
        return Result.ok(null);
    }

    /**
     * 退回:status 1/2 均可,意见必填;isPrecheck=true=盖章前预检退回(决策#18,
     * 留痕 action=precheck_reject;意见「盖章前预检」前缀由前端拼接传入)。
     */
    @PostMapping("/{flowId}/reject")
    public Result<Void> reject(@PathVariable Long flowId, @RequestBody(required = false) RejectBody body,
                               HttpServletRequest request) {
        requireAdmin(request);
        auditService.reject(uid(request), username(request), flowId,
                body == null ? null : body.opinion(),
                body != null && Boolean.TRUE.equals(body.isPrecheck()));
        return Result.ok(null);
    }

    // ---------- 到账明细(Task25,spec §4.8) ----------
    /** 到账明细查询(admin):按项目查明细列表+累计(实时 SUM,决策#16);缺参由 Service 给友好提示 */
    @GetMapping("/funding")
    public Result<Map<String, Object>> funding(@RequestParam(required = false) Long projectId,
                                               HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(fundingService.fundingList(projectId));
    }

    /** 到账录入(admin,源头=计财处/银行流水):录入即通知教师;与项目状态解耦(结题后仍可补录) */
    @PostMapping("/funding")
    public Result<Long> addFunding(@RequestBody FundingBody body, HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(fundingService.addFunding(uid(request), username(request), body));
    }

    /** 到账删除(admin,逻辑删):核销通知教师;累计实时 SUM 自动回落,不回写主表 */
    @DeleteMapping("/funding/{recordId}")
    public Result<Void> deleteFunding(@PathVariable Long recordId, HttpServletRequest request) {
        requireAdmin(request);
        fundingService.deleteFunding(uid(request), username(request), recordId);
        return Result.ok(null);
    }

    // ---------- 横向项目列表(Task25,spec §6.3/决策#21) ----------
    /** 全部横向项目聚合(admin,「横向项目管理」页数据源):到账累计/执行状态推导/在途流程单概要 */
    @GetMapping("/projects")
    public Result<List<Map<String, Object>>> projects(HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(fundingService.projectList());
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            throw new BizException("仅管理员可操作");
        }
    }

    private Long uid(HttpServletRequest request) {
        return (Long) request.getAttribute("uid");
    }

    private String username(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
}
