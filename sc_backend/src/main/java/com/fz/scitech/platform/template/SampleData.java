package com.fz.scitech.platform.template;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板试渲染内置静态样例数据(决策#34-③:代码常量,不建样例数据集)。
 * 供模板上传校验试渲染 / 冒烟测试使用,字段集与《占位符清单.md》一致。
 */
public final class SampleData {

    private SampleData() {
    }

    /** 按 tplType 取样例数据模型 */
    public static Map<String, Object> forType(String tplType) {
        return switch (tplType) {
            case "hx_register" -> register();
            case "hx_change" -> change();
            case "hx_acceptance" -> acceptance();
            case "hx_report" -> report();
            default -> throw new IllegalArgumentException("未知模板类型:" + tplType);
        };
    }

    /** 勾选组:按选中值输出 √/□ 串(√=U+221A 宋体自带字形;■涂黑已弃用,见 CLAUDE.md 踩坑#6) */
    public static String checks(String selected, String... options) {
        StringBuilder b = new StringBuilder();
        for (String opt : options) {
            if (b.length() > 0) {
                b.append("  ");
            }
            b.append(opt.equals(selected) ? "√" : "□").append(opt);
        }
        return b.toString();
    }

    private static Map<String, Object> base() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approvalNo", "HX2026-001");
        m.put("projectName", "某纺织企业智能化生产线改造技术服务项目");
        m.put("projectTypeChecks", checks("技术开发", "技术开发", "技术咨询", "技术服务"));
        m.put("leaderName", "张三");
        m.put("deptName", "智能制造与装备学院");
        return m;
    }

    /** 成员/学生固定行平铺:m1name..m8name(不足行补空串,模板空行原样保留) */
    private static void putFixedRows(Map<String, Object> m, String prefix, int rows,
                                      List<Map<String, Object>> people, String... fields) {
        for (int i = 1; i <= rows; i++) {
            Map<String, Object> p = i <= people.size() ? people.get(i - 1) : null;
            for (String f : fields) {
                m.put(prefix + i + f, p == null ? "" : String.valueOf(p.getOrDefault(f, "")));
            }
        }
    }

    private static List<Map<String, Object>> sampleMembers() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(teacher("张三", "智能制造与装备学院", "教授", "机械工程", "总体方案设计与技术把关"));
        list.add(teacher("李四", "智能制造与装备学院", "讲师", "自动化", "控制系统开发与联调"));
        list.add(teacher("王五", "校外人员", "高级工程师", "电气工程", "设备选型与现场实施"));
        return list;
    }

    private static Map<String, Object> teacher(String name, String unit, String title, String major, String duty) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("unit", unit);
        m.put("title", title);
        m.put("major", major);
        m.put("duty", duty);
        return m;
    }

    private static Map<String, Object> ratioMember(String name, String unit, String title, String major, String dutyRatio) {
        Map<String, Object> m = teacher(name, unit, title, major, null);
        m.put("dutyRatio", dutyRatio);
        return m;
    }

    private static Map<String, Object> register() {
        Map<String, Object> m = base();
        BigDecimal amount = new BigDecimal("355000.00");
        m.put("startEndDate", "2026年01月至2027年12月");
        m.put("clientName", "某某纺织有限公司");
        m.put("clientNatureChecks", checks("企业", "政府部门", "企业", "事业单位", "研究机构", "其他"));
        m.put("contractAmount", "355000.00");
        m.put("contractAmountCapital", RmbCapitalUtil.toCapital(amount));
        m.put("leaderPhone", "13800001111");
        m.put("leaderEmail", "zhangsan@cdtc.edu.cn");
        m.put("projectSummary", "【样例】项目背景:某纺织企业现有生产线人工干预多、联动性差,亟需智能化改造。"
                + "项目目标:完成生产线智能化改造方案设计与实施,交付技术报告与操作手册,实现自动化率提升至85%以上。"
                + "技术路线:采用传感器网络+PLC 集控+MES 看板三层架构,产学研联合实施。"
                + "预期成果:年产能提升约300万元,生产成本降低12%。");
        m.put("progressPlan", "【样例】2026年01月-2026年03月:实地调研,明确改造需求,制定技术路线。"
                + "2026年04月-2026年09月:方案设计,开展技术测试与评审。"
                + "2026年10月-2027年09月:设备升级与系统集成,联调测试,操作培训。"
                + "2027年10月-2027年12月:项目验收,提交运行报告,资料归档。");
        m.put("budgetBusiness", "80000.00");
        m.put("budgetLabor", "60000.00");
        m.put("budgetPerformance", "150000.00");
        m.put("budgetReception", "5000.00");
        m.put("budgetCollab", "40000.00");
        m.put("budgetOther", "20000.00");
        m.put("useSchoolAssetChecks", checks("是", "是", "否") + "以使用学校仪器设备和场地为主");
        m.put("chargeMgmtFeeChecks", "是否收取管理费:" + checks("是", "是", "否"));
        m.put("mgmtFeeAmount", "10000.00");
        m.put("hxRegisterDate", "");
        putFixedRows(m, "m", 6, sampleMembers(), "name", "unit", "title", "major", "duty");
        return m;
    }

    private static Map<String, Object> change() {
        Map<String, Object> m = base();
        m.put("leaderPhone", "13800001111");
        m.put("changeTypeChecks", "√项目经费 □执行周期 □项目负责人 □委托内容 □其他约定");
        m.put("changeContent", "【样例】因客户追加二期工位改造需求,合同经费由355000元调整为420000元,其余条款不变。");
        m.put("changeReason", "【样例】委托方生产订单增加,双方协商追加改造范围,需相应调整合同经费。");
        return m;
    }

    private static Map<String, Object> acceptance() {
        Map<String, Object> m = base();
        m.put("startEndDate", "2026年01月至2027年12月");
        m.put("clientName", "某某纺织有限公司");
        m.put("clientNatureChecks", checks("企业", "政府部门", "企业", "事业单位", "研究机构", "其他"));
        m.put("receivedAmountWan", "35.50");
        m.put("usedAmountWan", "34.80");
        List<Map<String, Object>> members = new ArrayList<>();
        members.add(ratioMember("张三", "智能制造与装备学院", "教授", "机械工程", "40%(14.2万元)"));
        members.add(ratioMember("李四", "智能制造与装备学院", "讲师", "自动化", "40%(14.2万元)"));
        members.add(ratioMember("王五", "校外人员", "高级工程师", "电气工程", "20%(7.1万元)"));
        putFixedRows(m, "m", 8, members, "name", "unit", "title", "major", "dutyRatio");
        List<Map<String, Object>> students = new ArrayList<>();
        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("name", "赵同学");
        s1.put("studentNo", "2023011234");
        s1.put("major", "机电一体化");
        s1.put("mentor", "张三");
        s1.put("duty", "传感器数据采集程序开发");
        students.add(s1);
        putFixedRows(m, "s", 4, students, "name", "studentNo", "major", "mentor", "duty");
        m.put("finishDate", "2027年12月20日");
        m.put("outcomeFormChecks", "√技术报告 □鉴定报告 √专利 □咨询报告 □其他");
        m.put("acceptSummary", "【样例】目标达成情况:完成全部改造内容,自动化率由原45%提升至88%,生产效率提高30%。"
                + "关键成果:提交技术文档5份,受理发明专利1项。"
                + "经费执行:各科目无超支。客户评价:验收合格,表达后续合作意向。");
        m.put("verifiedAmountWan", "35.50");
        return m;
    }

    private static Map<String, Object> report() {
        Map<String, Object> m = base();
        m.put("onTimeChecks", "是√  否□");
        m.put("outcomeFormChecks", "√技术报告 □鉴定报告 √专利 □咨询报告 □其他");
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add("一、项目目标达成情况");
        paragraphs.add("【样例】(一)技术服务内容与目标完成度:按合同约定完成生产线智能化改造全部内容,核心技术指标均达到或优于约定值。");
        paragraphs.add("二、研究过程总结");
        paragraphs.add("【样例】项目实施历经需求调研、方案设计、系统集成、联调优化四个阶段,实行周例会与里程碑双控机制。");
        paragraphs.add("三、关键工作成果");
        paragraphs.add("【样例】核心技术成果:智能排产算法一套、集控平台一套;受理发明专利1项。");
        paragraphs.add("四、总结与展望");
        paragraphs.add("【样例】项目全面完成合同任务,建议后续在能耗优化方向继续合作。");
        m.put("reportParagraphs", paragraphs);
        return m;
    }
}
