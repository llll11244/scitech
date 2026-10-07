package com.fz.scitech.hx;

import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.hx.dto.RejectBody;
import com.fz.scitech.hx.dto.ReviewBody;
import jakarta.servlet.http.HttpServletRequest;
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
 * 科技处端横向接口(Task24,spec §6.3):审批列表/通过/退回,全部仅 admin(spec §6.4;
 * viewer/teacher 拒)。教师重交入口在 HxController(resubmit 为教师本人操作)。
 */
@RestController
@RequestMapping("/hx")
public class HxAdminController {

    private final HxAuditService auditService;

    public HxAdminController(HxAuditService auditService) {
        this.auditService = auditService;
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
