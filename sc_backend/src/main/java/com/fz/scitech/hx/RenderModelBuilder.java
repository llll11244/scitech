package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fz.scitech.hx.entity.RpBudget;
import com.fz.scitech.hx.entity.RpHxFlow;
import com.fz.scitech.hx.entity.RpHxProject;
import com.fz.scitech.hx.entity.RpMember;
import com.fz.scitech.hx.entity.RpProject;
import com.fz.scitech.platform.template.RmbCapitalUtil;
import com.fz.scitech.system.entity.SysDict;
import com.fz.scitech.system.mapper.SysDictMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * rp_* 实体 → poi-tl 渲染 model(N3 三流程)。拼装口径与《占位符清单.md》四模板一致:
 * 通用勾选组=√/□ 在标签前(√=U+221A 宋体自带字形,踩坑#6,顺序=字典顺序);
 * 报告表"是否按期完成"按原表排版=标签在前(是√  否□);大写金额走 RmbCapitalUtil;
 * 日期 yyyy年MM月;万元两位小数;成员 m/学生 s 连续非空平铺(尾部不补空串,空行由模板
 * 预置占位符缺键清空渲染,超预置由 TemplateService.expandDynamicRows 扩行——Task18 契约)。
 */
@Component
public class RenderModelBuilder {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy年MM月");
    /** 预算科目→登记表占位符(占位符清单.md,字典 budget_subject_hx) */
    private static final Map<String, String> BUDGET_KEY = Map.of(
            "业务费", "budgetBusiness", "劳务费", "budgetLabor", "绩效", "budgetPerformance",
            "业务接待费", "budgetReception", "协作费", "budgetCollab", "其它", "budgetOther");

    private final SysDictMapper dictMapper;

    public RenderModelBuilder(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    /** 字典标签(启用序=sort_order 升序);勾选组顺序以此为准 */
    private List<String> dictLabels(String dictType) {
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictType, dictType).eq(SysDict::getStatus, 1)
                        .orderByAsc(SysDict::getSortOrder))
                .stream().map(SysDict::getDictLabel).collect(Collectors.toList());
    }

    /** 勾选组:selected 命中 √,其余 □(符号在标签前,顺序=字典顺序,与模板/样例 PDF 口径一致) */
    static String checks(List<String> options, String... selected) {
        Set<String> hit = selected == null ? Set.of() : new HashSet<>(Arrays.asList(selected));
        StringBuilder sb = new StringBuilder();
        for (String o : options) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(hit.contains(o) ? "√" : "□").append(o);
        }
        return sb.toString();
    }

    /** 逗号多选拆分+逐项 trim(null→空数组):防「项目经费, 执行周期」带空格导致勾选不命中(HxService 校验同口径) */
    static String[] splitTrim(String s) {
        return s == null ? new String[0]
                : Arrays.stream(s.split(",")).map(String::trim).toArray(String[]::new);
    }

    /** 报告表"是否按期完成"专用:原表排版=标签在前,两空格分隔(是√  否□) */
    static String onTimeChecks(Integer isOnTime) {
        return isOnTime != null && isOnTime == 1 ? "是√  否□" : "是□  否√";
    }

    /** 合同起止:yyyy年MM月 至 yyyy年MM月 */
    static String startEndDate(LocalDate s, LocalDate e) {
        return (s == null ? "" : YM.format(s)) + "至" + (e == null ? "" : YM.format(e));
    }

    /** 元→万元字符串(两位小数) */
    static String wan(BigDecimal yuan) {
        return yuan == null ? "" : yuan.movePointLeft(4).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 元金额字符串(两位小数) */
    static String money(BigDecimal yuan) {
        return yuan == null ? "" : yuan.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 登记表(hx_register):四个流程单中字段最全,含预算科目映射与成员固定行 */
    public Map<String, Object> buildRegister(RpProject p, RpHxProject hx, List<RpMember> members, List<RpBudget> budgets) {
        Map<String, Object> m = base(p);
        m.put("projectTypeChecks", checks(dictLabels("hx_project_type"), hx.getProjectType()));
        m.put("startEndDate", startEndDate(hx.getStartDate(), hx.getEndDate()));
        m.put("clientName", s(hx.getClientName()));
        m.put("clientNatureChecks", checks(dictLabels("client_nature"), hx.getClientNature()));
        m.put("contractAmount", money(hx.getContractAmount()));
        m.put("contractAmountCapital", hx.getContractAmount() == null ? "" : RmbCapitalUtil.toCapital(hx.getContractAmount()));
        m.put("leaderPhone", s(p.getLeaderPhone()));      // sys_user.phone,HxService 填充
        m.put("leaderEmail", s(hx.getLeaderEmail()));
        m.put("projectSummary", s(hx.getProjectSummary()));
        m.put("progressPlan", s(hx.getProgressPlan()));
        Map<String, String> budgetMap = budgets.stream()
                .collect(Collectors.toMap(RpBudget::getSubject, b -> money(b.getAmount()), (a, b) -> a));
        BUDGET_KEY.forEach((subject, key) -> m.put(key, budgetMap.getOrDefault(subject, "")));
        m.put("useSchoolAssetChecks", checks(List.of("是", "否"),
                hx.getUseSchoolAsset() != null && hx.getUseSchoolAsset() == 1 ? "是" : "否"));
        boolean charge = hx.getChargeMgmtFee() != null && hx.getChargeMgmtFee() == 1;
        m.put("chargeMgmtFeeChecks", checks(List.of("是", "否"), charge ? "是" : "否"));
        m.put("mgmtFeeAmount", charge ? money(hx.getMgmtFeeAmount()) : "");
        m.put("hxRegisterDate", ""); // 科技处确认时才写(N4 终版渲染)
        putRows(m, "m", teachers(members), "name", "unit", "title", "major", "duty");
        return m;
    }

    /** 变更表(hx_change):leaderPhone 由 HxService 从 sys_user 查出传入 */
    public Map<String, Object> buildChange(RpProject p, RpHxProject hx, RpHxFlow flow, String leaderPhone) {
        Map<String, Object> m = base(p);
        m.put("projectTypeChecks", checks(dictLabels("hx_project_type"), hx.getProjectType()));
        m.put("leaderPhone", s(leaderPhone));
        String[] types = splitTrim(flow.getChangeType());
        m.put("changeTypeChecks", checks(dictLabels("change_type"), types));
        m.put("changeContent", s(flow.getChangeContent()));
        m.put("changeReason", s(flow.getChangeReason()));
        return m;
    }

    /** 验收登记表(hx_acceptance):教师分工比例+金额,学生行 s 前缀;无 onTimeChecks(该字段仅在报告表渲染) */
    public Map<String, Object> buildAcceptance(RpProject p, RpHxProject hx, RpHxFlow flow,
                                               List<RpMember> members, BigDecimal receivedTotal) {
        Map<String, Object> m = base(p);
        m.put("projectTypeChecks", checks(dictLabels("hx_project_type"), hx.getProjectType()));
        m.put("startEndDate", startEndDate(hx.getStartDate(), hx.getEndDate()));
        m.put("clientName", s(hx.getClientName()));
        m.put("clientNatureChecks", checks(dictLabels("client_nature"), hx.getClientNature()));
        m.put("receivedAmountWan", wan(receivedTotal));
        m.put("usedAmountWan", wan(flow.getUsedAmount()));
        List<RpMember> teachers = teachers(members);
        putRows(m, "m", teachers, "name", "unit", "title", "major");
        for (int i = 0; i < teachers.size(); i++) {
            RpMember t = teachers.get(i);
            String ratio = t.getRatio() == null ? ""
                    : t.getRatio().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String amtWan = wan(t.getAmount());
            String cell = ratio.isEmpty() ? "" : ratio + "%";
            if (!amtWan.isEmpty()) {
                cell = cell + "(" + amtWan + "万元)";
            }
            m.put("m" + (i + 1) + "dutyRatio", cell);
        }
        putRows(m, "s", students(members), "name", "studentNo", "major", "mentor", "duty");
        m.put("finishDate", ""); // 验收确认日,N4 终版渲染
        m.put("outcomeFormChecks", checks(dictLabels("hx_outcome_form"), splitTrim(flow.getOutcomeForm())));
        m.put("acceptSummary", s(flow.getAcceptSummary()));
        m.put("verifiedAmountWan", ""); // 核定 N4 录入
        return m;
    }

    /** 验收报告(hx_report):hx 供 projectTypeChecks(REPORT 占位符集含此键,类型在 rp_hx_project) */
    public Map<String, Object> buildReport(RpProject p, RpHxProject hx, RpHxFlow flow) {
        Map<String, Object> m = base(p);
        m.put("onTimeChecks", onTimeChecks(flow.getIsOnTime()));
        m.put("projectTypeChecks", checks(dictLabels("hx_project_type"), hx.getProjectType()));
        m.put("outcomeFormChecks", checks(dictLabels("hx_outcome_form"), splitTrim(flow.getOutcomeForm())));
        // 区块对 {{?reportParagraphs}}{{this}}{{/reportParagraphs}}:poi-tl 1.12.2 的 {{this}} 只解析
        // Map 元素键(List<String> 元素实测渲染为空),故每段包为 {"this": 段文本}
        m.put("reportParagraphs", flow.getReportContent() == null || flow.getReportContent().isBlank()
                ? List.of() : Arrays.stream(flow.getReportContent().split("\\n\\n"))
                        .map(String::trim).filter(x -> !x.isEmpty())
                        .map(t -> Map.of("this", (Object) t)).collect(Collectors.toList()));
        return m;
    }

    /** 公共头:编号/名称/负责人/院部(deptName/leaderPhone 为 RpProject 非表列,HxService 填充) */
    private Map<String, Object> base(RpProject p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approvalNo", s(p.getApprovalNo()));
        m.put("projectName", s(p.getName()));
        m.put("leaderName", s(p.getLeaderName()));
        m.put("deptName", s(p.getDeptName()));
        return m;
    }

    /** 非学生成员(教师/校外人员,平铺 m 行;调用方保证顺序=sort_no) */
    private static List<RpMember> teachers(List<RpMember> members) {
        return members.stream().filter(x -> !"学生".equals(x.getMemberType())).collect(Collectors.toList());
    }

    /** 学生成员(平铺 s 行) */
    private static List<RpMember> students(List<RpMember> members) {
        return members.stream().filter(x -> "学生".equals(x.getMemberType())).collect(Collectors.toList());
    }

    /**
     * 固定行平铺(Task18 契约):连续非空行 i=1..size,仅平铺实际条数、尾部不补空串——
     * 空行由模板预置占位符缺键清空渲染,超出预置条数由 expandDynamicRows 按连续非空计数扩行。
     */
    private static void putRows(Map<String, Object> m, String prefix, List<RpMember> people, String... fields) {
        for (int i = 0; i < people.size(); i++) {
            RpMember p = people.get(i);
            for (String f : fields) {
                m.put(prefix + (i + 1) + f, val(p, f));
            }
        }
    }

    private static String val(RpMember p, String field) {
        return switch (field) {
            case "name" -> s(p.getName());
            case "unit" -> s(p.getUnit());
            case "title" -> s(p.getTitle());
            case "major" -> s(p.getMajor());
            case "duty" -> s(p.getDuty());
            case "studentNo" -> s(p.getStudentNo());
            case "mentor" -> s(p.getMentor());
            default -> "";
        };
    }

    private static String s(String v) {
        return v == null ? "" : v;
    }
}
