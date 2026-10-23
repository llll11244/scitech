package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.hx.dto.AcceptanceSaveDTO;
import com.fz.scitech.hx.dto.ChangeSaveDTO;
import com.fz.scitech.hx.dto.FlowDetailVO;
import com.fz.scitech.hx.dto.RegisterSaveDTO;
import com.fz.scitech.system.entity.SysDept;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysDeptMapper;
import com.fz.scitech.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师端横向接口(均登录;本人数据隔离在 Service 层强制)。
 * 交付下载:docx=主交付;pdf 入口前端按 deliver_pdf_visible 显隐(后端不做开关拦截,文件本就隐藏留存)。
 */
@RestController
@RequestMapping("/hx")
public class HxController {

    private final HxService hxService;
    private final FundingService fundingService;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    public HxController(HxService hxService, FundingService fundingService, SysUserMapper userMapper,
                        SysDeptMapper deptMapper) {
        this.hxService = hxService;
        this.fundingService = fundingService;
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
    }

    /**
     * 用户简单搜索:变更负责人 el-select 远程搜索用(teacher/admin 可用;viewer 拒——
     * 不参与横向,spec §6.4,Task28 收紧)。
     * 按用户名/姓名 LIKE(通配符 %/_ 转义,防一个 % 全表捞用户),启用账号,至多 20 条;
     * 只回 id/username/name 三列,不泄露其余资料。
     */
    @GetMapping("/leaders")
    public Result<List<Map<String, Object>>> leaders(@RequestParam(required = false) String q,
                                                     HttpServletRequest request) {
        if ("viewer".equals(role(request))) {
            throw new BizException("只读账号不支持负责人搜索");
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        if (q == null || q.isBlank()) {
            return Result.ok(rows);
        }
        String kw = escapeLike(q.trim());
        for (SysUser u : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .and(w -> w.like(SysUser::getUsername, kw).or().like(SysUser::getRealName, kw))
                .orderByAsc(SysUser::getId)
                .last("limit 20"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("name", u.getRealName());
            rows.add(m);
        }
        return Result.ok(rows);
    }

    /**
     * 工号查人(HTML表单转正 2026-10-21):登记表成员区输工号自动带出信息。
     * username 精确匹配+启用账号,至多 1 条;回 {username,name,deptName,title}(专业系统无,教师手填);
     * 查无返 data=null(前端整行手填并提示)。权限同 /hx/leaders(teacher/admin,viewer 拒)。
     */
    @GetMapping("/member-lookup")
    public Result<Map<String, Object>> memberLookup(@RequestParam(required = false) String q,
                                                    HttpServletRequest request) {
        if ("viewer".equals(role(request))) {
            throw new BizException("只读账号不支持工号查询");
        }
        if (q == null || q.isBlank()) {
            return Result.ok(null);
        }
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, q.trim())
                .eq(SysUser::getStatus, 1)
                .last("limit 1"));
        if (u == null) {
            return Result.ok(null);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("name", u.getRealName());
        SysDept d = u.getDeptId() == null ? null : deptMapper.selectById(u.getDeptId());
        m.put("deptName", d == null ? null : d.getName());
        m.put("title", u.getTitle());
        return Result.ok(m);
    }

    /** 登记草稿保存(flowId 空=新建,非空=整体覆盖) */
    @PostMapping("/register/draft")
    public Result<Long> registerDraft(@RequestBody RegisterSaveDTO dto, HttpServletRequest request) {
        return Result.ok(hxService.saveRegisterDraft(uid(request), username(request), dto));
    }

    @PostMapping("/register/submit")
    public Result<Void> registerSubmit(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        hxService.submitRegister(uid(request), username(request), body.get("flowId"));
        return Result.ok(null);
    }

    /** 变更草稿保存(flowId 空=新建;draft 不校验项目确认态,提交才强校验 audit_status=2) */
    @PostMapping("/change/draft")
    public Result<Long> changeDraft(@RequestBody ChangeSaveDTO dto, HttpServletRequest request) {
        return Result.ok(hxService.saveChangeDraft(uid(request), username(request), dto));
    }

    /** 变更提交(0→1,拍 before_json 快照+渲 hx_change 待签双出) */
    @PostMapping("/change/submit")
    public Result<Void> changeSubmit(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        hxService.submitChange(uid(request), username(request), body.get("flowId"));
        return Result.ok(null);
    }

    /** 验收草稿保存(每项目至多 1 张;成员全量覆盖补 ratio/amount/学号/导师) */
    @PostMapping("/acceptance/draft")
    public Result<Long> acceptanceDraft(@RequestBody AcceptanceSaveDTO dto, HttpServletRequest request) {
        return Result.ok(hxService.saveAcceptanceDraft(uid(request), username(request), dto));
    }

    /** 验收提交(0→1,渲两张待签:hx_acceptance+hx_report,双出×2=4 附件) */
    @PostMapping("/acceptance/submit")
    public Result<Void> acceptanceSubmit(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        hxService.submitAcceptance(uid(request), username(request), body.get("flowId"));
        return Result.ok(null);
    }

    @PostMapping("/{flowId}/withdraw")
    public Result<Void> withdraw(@PathVariable Long flowId, HttpServletRequest request) {
        hxService.withdraw(uid(request), username(request), flowId);
        return Result.ok(null);
    }

    /** 退回单修改重交(Task24:4→1,重渲待签双出,旧待签+旧扫描件作废;教师本人) */
    @PostMapping("/{flowId}/resubmit")
    public Result<Void> resubmit(@PathVariable Long flowId, HttpServletRequest request) {
        hxService.resubmit(uid(request), username(request), flowId);
        return Result.ok(null);
    }

    /** 盖章扫描件上传(重传同类型覆盖,旧件作废) */
    @PostMapping("/{flowId}/scan")
    public Result<Long> scan(@PathVariable Long flowId, @RequestParam String docType,
                             @RequestParam MultipartFile file, HttpServletRequest request) {
        return Result.ok(hxService.uploadScan(uid(request), username(request), flowId, file, docType));
    }

    /**
     * 合同扫描件上传(Task25,通用附件 ruling):biz_type=hx_apply,doc_type=合同;
     * 本人流程单任何状态可传,同 docType 重传覆盖(旧件逻辑删),不触发状态迁移;
     * 落盘 upload/hx/{pid}/apply/{flowId}/。
     */
    @PostMapping("/{flowId}/attach")
    public Result<Long> attach(@PathVariable Long flowId,
                               @RequestParam(defaultValue = "合同") String docType,
                               @RequestParam MultipartFile file, HttpServletRequest request) {
        return Result.ok(fundingService.uploadApply(uid(request), username(request), flowId, docType, file));
    }

    /** 到账明细只读(Task25,spec §6.1/§4.8):本人项目强制;明细+累计(科技处录入,教师只读) */
    @GetMapping("/{projectId}/funding")
    public Result<Map<String, Object>> funding(@PathVariable Long projectId, HttpServletRequest request) {
        return Result.ok(fundingService.teacherFunding(uid(request), projectId));
    }

    /** 删草稿(仅 status=0;登记草稿连带逻辑删项目/成员/预算/附件;有到账禁删,决策#34) */
    @DeleteMapping("/{flowId}")
    public Result<Void> deleteDraft(@PathVariable Long flowId, HttpServletRequest request) {
        hxService.deleteDraft(uid(request), flowId);
        return Result.ok(null);
    }

    /** 取消在途变更单(五轮反馈):status∈{0,1,4,5},作废流程单+扫描件,通知科技处;审核中(2)先撤回 */
    @PostMapping("/{flowId}/cancel")
    public Result<Void> cancelChange(@PathVariable Long flowId, HttpServletRequest request) {
        hxService.cancelChange(uid(request), username(request), flowId);
        return Result.ok(null);
    }

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my(HttpServletRequest request) {
        return Result.ok(hxService.myProjects(uid(request)));
    }

    @GetMapping("/{flowId}")
    public Result<FlowDetailVO> detail(@PathVariable Long flowId, HttpServletRequest request) {
        return Result.ok(hxService.flowDetail(uid(request), role(request), flowId));
    }

    /** 待签 Word 主交付下载(inline 预览同接口);doc=模板类型,验收流程有 hx_acceptance/hx_report 两张(默认 hx_acceptance) */
    @GetMapping("/{flowId}/deliver/docx")
    public void deliverDocx(@PathVariable Long flowId,
                            @RequestParam(defaultValue = "hx_acceptance") String doc,
                            HttpServletRequest request, HttpServletResponse response) throws IOException {
        hxService.writeDeliver(uid(request), role(request), flowId, stem(doc, "docx"), response);
    }

    @GetMapping("/{flowId}/deliver/pdf")
    public void deliverPdf(@PathVariable Long flowId,
                           @RequestParam(defaultValue = "hx_acceptance") String doc,
                           HttpServletRequest request, HttpServletResponse response) throws IOException {
        hxService.writeDeliver(uid(request), role(request), flowId, stem(doc, "pdf"), response);
    }

    /**
     * 终版交付下载(Task26,spec §6.2「终版数据 PDF 归档后可看」):验收确认时渲染的终版件,
     * 教师本人/admin 可取;biz_type=hx_final_docx/hx_final_pdf/hx_final_report_*
     * (doc 参数推导同 stem 规则+final 前缀);仅 status=3 流程单存在(Service 层校验)。
     */
    @GetMapping("/{flowId}/deliver-final/docx")
    public void deliverFinalDocx(@PathVariable Long flowId,
                                 @RequestParam(defaultValue = "hx_acceptance") String doc,
                                 HttpServletRequest request, HttpServletResponse response) throws IOException {
        hxService.writeDeliver(uid(request), role(request), flowId, finalStem(doc, "docx"), true, response);
    }

    @GetMapping("/{flowId}/deliver-final/pdf")
    public void deliverFinalPdf(@PathVariable Long flowId,
                                @RequestParam(defaultValue = "hx_acceptance") String doc,
                                HttpServletRequest request, HttpServletResponse response) throws IOException {
        hxService.writeDeliver(uid(request), role(request), flowId, finalStem(doc, "pdf"), true, response);
    }

    /**
     * 附件内容下载(spec §6.2):扫描件/申报佐证等查看用。
     * 白名单 hx_scan/hx_apply/hx_extra(待签渲染件走既有 /deliver 接口,不重复开口);
     * 鉴权同流程详情:教师 owner 校验、admin 直查(Service 层)。
     */
    @GetMapping("/attachment/{id}/download")
    public void downloadAttachment(@PathVariable Long id, HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        hxService.writeAttachment(uid(request), role(request), id, response);
    }

    /** doc(模板类型)→附件 biz_type:hx_report→hx_report_docx/hx_report_pdf,其余→hx_docx/hx_pdf(与 PdfRenderService stem 同规则) */
    private String stem(String tplType, String ext) {
        return ("hx_report".equals(tplType) ? "hx_report_" : "hx_") + ext;
    }

    /** 终版 biz_type:同 stem 规则+final 段(hx_final_docx/hx_final_report_pdf,与 PdfRenderService 终版口径一致) */
    private String finalStem(String tplType, String ext) {
        return "hx_final" + ("hx_report".equals(tplType) ? "_report" : "") + "_" + ext;
    }

    private Long uid(HttpServletRequest request) {
        return (Long) request.getAttribute("uid");
    }

    private String username(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }

    private String role(HttpServletRequest request) {
        return (String) request.getAttribute("role");
    }

    /**
     * LIKE 通配符转义(Task28 收紧):\ % _ 前加 MySQL 默认转义符反斜杠,
     * 防 q=% 一类输入当通配符全表匹配(MP like 的值是绑定参数,注入无虞,仅通配语义需封)。
     */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
