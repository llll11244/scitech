package com.fz.scitech.platform.template;

import java.util.HashSet;
import java.util.Set;

/**
 * 四类模板必备占位符常量(与 templates-src/占位符清单.md 一致,Task 10 制作基线)。
 * 上传校验双向比对:模板标签必须与常量集完全一致(缺失/多余均拒)。
 */
public final class TemplatePlaceholders {

    private TemplatePlaceholders() {
    }

    private static Set<String> set(String... keys) {
        return Set.of(keys);
    }

    /** m{i} 固定行:如 m1name..m8duty */
    private static String[] fixedRows(String prefix, int rows, String... fields) {
        Set<String> s = new HashSet<>();
        for (int i = 1; i <= rows; i++) {
            for (String f : fields) {
                s.add(prefix + i + f);
            }
        }
        return s.toArray(new String[0]);
    }

    public static final Set<String> REGISTER = set(builder(
            new String[]{"approvalNo", "projectName", "projectTypeChecks", "startEndDate", "clientName",
                    "clientNatureChecks", "contractAmount", "contractAmountCapital", "leaderName", "deptName",
                    "leaderPhone", "leaderEmail", "projectSummary", "progressPlan",
                    "budgetBusiness", "budgetLabor", "budgetPerformance", "budgetReception", "budgetCollab", "budgetOther",
                    "useSchoolAssetChecks", "chargeMgmtFeeChecks", "mgmtFeeAmount", "hxRegisterDate"},
            fixedRows("m", 6, "name", "unit", "title", "major", "duty")));

    public static final Set<String> CHANGE = set("approvalNo", "projectName", "projectTypeChecks",
            "leaderName", "leaderPhone", "changeTypeChecks", "changeContent", "changeReason");

    public static final Set<String> ACCEPTANCE = set(builder(
            new String[]{"approvalNo", "projectName", "projectTypeChecks", "startEndDate", "clientName",
                    "clientNatureChecks", "receivedAmountWan", "usedAmountWan", "leaderName", "deptName",
                    "finishDate", "outcomeFormChecks", "acceptSummary", "verifiedAmountWan"},
            fixedRows("m", 8, "name", "unit", "title", "major", "dutyRatio"),
            fixedRows("s", 5, "name", "studentNo", "major", "mentor", "duty")));

    /** 报告正文用区块对循环:{ {?reportParagraphs}}{{this}}{{/reportParagraphs} } */
    public static final Set<String> REPORT = set("approvalNo", "projectName", "onTimeChecks",
            "projectTypeChecks", "outcomeFormChecks", "reportParagraphs", "this");

    public static Set<String> forType(String tplType) {
        return switch (tplType) {
            case "hx_register" -> REGISTER;
            case "hx_change" -> CHANGE;
            case "hx_acceptance" -> ACCEPTANCE;
            case "hx_report" -> REPORT;
            default -> throw new IllegalArgumentException("未知模板类型:" + tplType);
        };
    }

    /** 页数上限(hx_report 不限,给个大数) */
    public static int maxPages(String tplType) {
        return "hx_report".equals(tplType) ? 200 : 2;
    }

    private static String[] builder(String[] base, String[]... more) {
        Set<String> all = new HashSet<>(Set.of(base));
        for (String[] arr : more) {
            all.addAll(Set.of(arr));
        }
        return all.toArray(new String[0]);
    }
}
