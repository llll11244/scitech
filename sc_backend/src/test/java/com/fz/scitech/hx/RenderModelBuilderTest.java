package com.fz.scitech.hx;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fz.scitech.hx.entity.RpBudget;
import com.fz.scitech.hx.entity.RpHxFlow;
import com.fz.scitech.hx.entity.RpHxProject;
import com.fz.scitech.hx.entity.RpMember;
import com.fz.scitech.hx.entity.RpProject;
import com.fz.scitech.platform.template.TemplateService;
import com.fz.scitech.system.entity.SysDict;
import com.fz.scitech.system.mapper.SysDictMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RenderModelBuilder 组装口径:勾选组/大写金额/日期/万元换算/成员平铺契约(Task19)。
 * 勾选组约定(踩坑#6,拼法参照 SampleData):通用组=√/□ 在标签前,顺序=字典顺序;
 * 报告表"是否按期完成"为原表排版=标签在前(是√  否□)。
 * 平铺契约:仅平铺实际条数(连续非空),尾部不补空串——空行由模板预置占位符缺键清空渲染(Task18 expandDynamicRows 同契约)。
 */
class RenderModelBuilderTest {

    private final SysDictMapper dictMapper = mock(SysDictMapper.class);
    private final RenderModelBuilder builder = new RenderModelBuilder(dictMapper);
    /** dict_type → 字典行(mock 存储,selectList 按 wrapper 参数值分发) */
    private final Map<String, List<SysDict>> dictStore = new HashMap<>();

    RenderModelBuilderTest() {
        givenDict("hx_project_type", "技术开发", "技术咨询", "技术服务");
        givenDict("hx_client_nature", "政府部门", "企业", "事业单位", "研究机构", "其他");
        givenDict("hx_outcome_form", "技术报告", "鉴定报告", "专利", "咨询报告", "其他");
        givenDict("hx_change_type", "项目经费", "执行周期", "项目负责人", "委托内容", "其他约定");
        // dictLabels 走 selectList(LambdaQueryWrapper),按 wrapper 参数值(含 dict_type 字符串)分发回字典行;
        // MP 3.5.7 参数惰性填充:须先触发 getSqlSegment() 渲染,paramNameValuePairs 才有值
        when(dictMapper.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<?> w = inv.getArgument(0);
            w.getSqlSegment();
            return dictStore.entrySet().stream()
                    .filter(e -> w.getParamNameValuePairs().containsValue(e.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElseGet(List::of);
        });
    }

    @BeforeAll
    static void initDictTableInfo() {
        // LambdaQueryWrapper 方法引用依赖 MyBatis-Plus TableInfo 缓存,纯单测无 Spring 需手动注册
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDict.class);
    }

    private void givenDict(String type, String... labels) {
        List<SysDict> rows = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            SysDict d = new SysDict();
            d.setDictType(type);
            d.setDictValue(labels[i]);
            d.setDictLabel(labels[i]);
            d.setSortOrder(i + 1);
            d.setStatus(1);
            rows.add(d);
        }
        dictStore.put(type, rows);
    }

    // ---------- 测试数据工厂 ----------

    private RpProject project() {
        RpProject p = new RpProject();
        p.setApprovalNo("HX2026-001");
        p.setName("某纺织企业智能化生产线改造技术服务项目");
        p.setLeaderName("张三");
        p.setDeptName("智能制造与装备学院");
        p.setLeaderPhone("13800001111");
        return p;
    }

    private RpHxProject hxProject() {
        RpHxProject hx = new RpHxProject();
        hx.setProjectType("技术开发");
        hx.setClientName("某某纺织有限公司");
        hx.setClientNature("企业");
        hx.setContractAmount(new BigDecimal("355000.00"));
        hx.setStartDate(LocalDate.of(2026, 1, 1));
        hx.setEndDate(LocalDate.of(2027, 12, 31));
        hx.setUseSchoolAsset(1);
        hx.setChargeMgmtFee(1);
        hx.setMgmtFeeAmount(new BigDecimal("10000.00"));
        hx.setLeaderEmail("zhangsan@cdtc.edu.cn");
        return hx;
    }

    private RpMember teacher() {
        RpMember m1 = new RpMember();
        m1.setName("张三");
        m1.setUnit("智能制造与装备学院");
        m1.setTitle("教授");
        m1.setMajor("机械工程");
        m1.setDuty("总体方案设计");
        m1.setMemberType("教师");
        return m1;
    }

    private RpBudget businessBudget() {
        RpBudget b1 = new RpBudget();
        b1.setSubject("业务费");
        b1.setAmount(new BigDecimal("80000.00"));
        return b1;
    }

    // ---------- 用例 ----------

    @Test
    void registerModelChecksAndCapitalAndRows() {
        Map<String, Object> m = builder.buildRegister(project(), hxProject(), List.of(teacher()), List.of(businessBudget()));

        assertEquals("√技术开发 □技术咨询 □技术服务", m.get("projectTypeChecks"), "勾选组=√选中+□未选,顺序=字典顺序");
        assertEquals("□政府部门 √企业 □事业单位 □研究机构 □其他", m.get("clientNatureChecks"));
        assertEquals("355000.00", m.get("contractAmount"));
        assertTrue(((String) m.get("contractAmountCapital")).startsWith("叁拾伍万伍仟"), "大写金额");
        assertEquals("2026年01月至2027年12月", m.get("startEndDate"));
        assertEquals("80000.00", m.get("budgetBusiness"), "预算科目→占位符映射");
        assertEquals("", m.get("budgetLabor"), "未填报科目补空串");
        assertEquals("√是 □否", m.get("useSchoolAssetChecks"));
        assertEquals("√是 □否", m.get("chargeMgmtFeeChecks"));
        assertEquals("10000.00", m.get("mgmtFeeAmount"));
        assertEquals("13800001111", m.get("leaderPhone"), "电话=sys_user.phone(HxService 填充 p.leaderPhone)");
        assertEquals("zhangsan@cdtc.edu.cn", m.get("leaderEmail"));
        assertEquals("", m.get("hxRegisterDate"), "科技处确认时才写(N4 终版)");
        assertEquals("张三", m.get("m1name"));
        assertEquals("机械工程", m.get("m1major"));
        assertNull(m.get("m2name"), "不足行不平铺(契约:仅实际条数,尾部空行由模板预置渲染)");

        // 管理费分支:不收管理费→勾"否"、金额空
        RpHxProject noFee = hxProject();
        noFee.setChargeMgmtFee(0);
        Map<String, Object> m2 = builder.buildRegister(project(), noFee, List.of(teacher()), List.of(businessBudget()));
        assertEquals("□是 √否", m2.get("chargeMgmtFeeChecks"));
        assertEquals("", m2.get("mgmtFeeAmount"), "不收管理费时金额不出");
    }

    @Test
    void changeModelMultiSelectChecks() {
        RpHxFlow flow = new RpHxFlow();
        flow.setChangeType("项目经费,执行周期");
        flow.setChangeContent("经费调整为420000元,周期延长3个月");
        flow.setChangeReason("委托方追加改造范围");

        Map<String, Object> m = builder.buildChange(project(), hxProject(), flow, "13800001111");

        assertEquals("√项目经费 √执行周期 □项目负责人 □委托内容 □其他约定", m.get("changeTypeChecks"), "多选勾选组");
        assertEquals("√技术开发 □技术咨询 □技术服务", m.get("projectTypeChecks"));
        assertEquals("13800001111", m.get("leaderPhone"));
        assertEquals("经费调整为420000元,周期延长3个月", m.get("changeContent"));
        assertEquals("委托方追加改造范围", m.get("changeReason"));

        // 变更类型为空(纯草稿)→全部未勾
        Map<String, Object> m2 = builder.buildChange(project(), hxProject(), new RpHxFlow(), null);
        assertEquals("□项目经费 □执行周期 □项目负责人 □委托内容 □其他约定", m2.get("changeTypeChecks"));
        assertEquals("", m2.get("leaderPhone"));
    }

    @Test
    void acceptanceModelRatioAndStudents() {
        RpHxFlow flow = new RpHxFlow();
        flow.setIsOnTime(1);
        flow.setOutcomeForm("技术报告,专利");
        flow.setAcceptSummary("完成全部内容");
        flow.setUsedAmount(new BigDecimal("348000.00"));
        RpMember t = teacher();
        t.setRatio(new BigDecimal("40.00"));
        t.setAmount(new BigDecimal("142000.00"));
        RpMember stu = new RpMember();
        stu.setMemberType("学生");
        stu.setName("赵同学");
        stu.setStudentNo("2023011234");
        stu.setMajor("机电一体化");
        stu.setMentor("张三");
        stu.setDuty("数据采集开发");

        Map<String, Object> m = builder.buildAcceptance(project(), hxProject(), flow,
                List.of(t, stu), new BigDecimal("355000.00"));

        assertEquals("35.50", m.get("receivedAmountWan"), "到账万元=元÷10000 两位小数");
        assertEquals("34.80", m.get("usedAmountWan"));
        assertEquals("√技术报告 □鉴定报告 √专利 □咨询报告 □其他", m.get("outcomeFormChecks"), "多选勾选组");
        assertEquals("40%(14.20万元)", m.get("m1dutyRatio"), "比例+金额拼串(比例去尾零,万元两位小数)");
        assertEquals("赵同学", m.get("s1name"), "学生行单独平铺 s 前缀");
        assertEquals("2023011234", m.get("s1studentNo"));
        assertEquals("张三", m.get("s1mentor"));
        assertNull(m.get("s2name"), "学生行同样仅平铺实际条数");
        assertNull(m.get("m2name"));
        assertEquals("完成全部内容", m.get("acceptSummary"));
        assertEquals("", m.get("finishDate"), "验收确认日 N4 终版渲染");
        assertEquals("", m.get("verifiedAmountWan"), "核定金额 N4 录入");
        assertFalse(m.containsKey("onTimeChecks"), "验收登记表占位符集无 onTimeChecks(是否按期仅在报告表渲染,见占位符清单)");
    }

    @Test
    void reportModelParagraphs() {
        RpHxFlow flow = new RpHxFlow();
        flow.setIsOnTime(0);
        flow.setOutcomeForm("技术报告");
        flow.setReportContent("一、目标达成情况\n\n【正文】完成。\n\n二、总结");

        Map<String, Object> m = builder.buildReport(project(), hxProject(), flow);

        assertEquals(List.of(Map.of("this", "一、目标达成情况"), Map.of("this", "【正文】完成。"), Map.of("this", "二、总结")),
                m.get("reportParagraphs"),
                "报告正文按\\n\\n分段,每段包为 {this:段文本}(poi-tl 1.12.2 区块对 {{this}} 只解析 Map 键,List<String> 实测渲染为空)");
        assertEquals("是□  否√", m.get("onTimeChecks"), "报告表按期勾选=标签在前(原表排版),未按期=否打勾");
        assertEquals("√技术开发 □技术咨询 □技术服务", m.get("projectTypeChecks"), "报告表占位符集含 projectTypeChecks");
        assertEquals("√技术报告 □鉴定报告 □专利 □咨询报告 □其他", m.get("outcomeFormChecks"));

        // 按期完成 + 空正文
        RpHxFlow onTimeBlank = new RpHxFlow();
        onTimeBlank.setIsOnTime(1);
        Map<String, Object> m2 = builder.buildReport(project(), hxProject(), onTimeBlank);
        assertEquals("是√  否□", m2.get("onTimeChecks"));
        assertEquals(List.of(), m2.get("reportParagraphs"), "空正文=空分段列表");
    }

    @Test
    void modelsRenderRealTemplatesWithoutResidue() throws Exception {
        // 平铺契约实证:仅平铺实际条数(1 教师/1 学生),真实模板渲染后不应残留 {{..}}
        //(模板预置空行的占位符缺键,poi-tl 清空渲染;TemplateService 仅用其 poi-tl 渲染,不依赖转换器)
        TemplateService tplSvc = new TemplateService(null, null, "target/test-upload");

        Map<String, Object> reg = builder.buildRegister(project(), hxProject(), List.of(teacher()), List.of(businessBudget()));
        File regOut = new File("target/test-task19-register.docx");
        tplSvc.renderDocx("hx_register", new File("../templates-src/hx_register.docx"), regOut, reg);
        String regXml = docXml(regOut.toPath());
        assertTrue(!regXml.contains("{{"), "登记表渲染后不应残留占位符(缺键如 m2name 应被清空)");
        assertTrue(regXml.contains("√技术开发") && regXml.contains("80000.00") && regXml.contains("张三"));
        assertTrue(!regXml.contains("FF0000"), "渲染值不得带红色(原版红色为示例样式,模板已清红)");

        RpHxFlow flow = new RpHxFlow();
        flow.setOutcomeForm("技术报告,专利");
        flow.setUsedAmount(new BigDecimal("348000.00"));
        RpMember t = teacher();
        t.setRatio(new BigDecimal("40.00"));
        t.setAmount(new BigDecimal("142000.00"));
        RpMember stu = new RpMember();
        stu.setMemberType("学生");
        stu.setName("赵同学");
        stu.setStudentNo("2023011234");
        stu.setMajor("机电一体化");
        stu.setMentor("张三");
        stu.setDuty("数据采集开发");
        Map<String, Object> acc = builder.buildAcceptance(project(), hxProject(), flow, List.of(t, stu),
                new BigDecimal("355000.00"));
        File accOut = new File("target/test-task19-acceptance.docx");
        tplSvc.renderDocx("hx_acceptance", new File("../templates-src/hx_acceptance.docx"), accOut, acc);
        String accXml = docXml(accOut.toPath());
        assertTrue(!accXml.contains("{{"), "验收表渲染后不应残留占位符(m/s 预置空行缺键清空)");
        assertTrue(accXml.contains("40%(14.20万元)") && accXml.contains("赵同学"));
        assertTrue(!accXml.contains("FF0000"), "验收表渲染值不得带红色(成员表占位符曾克隆红色段落标记,2026-10-08 修复)");

        // 变更表:多选勾选组
        RpHxFlow change = new RpHxFlow();
        change.setChangeType("项目经费,执行周期");
        change.setChangeContent("经费调整为420000元");
        change.setChangeReason("委托方追加改造范围");
        Map<String, Object> chg = builder.buildChange(project(), hxProject(), change, "13800001111");
        File chgOut = new File("target/test-task19-change.docx");
        tplSvc.renderDocx("hx_change", new File("../templates-src/hx_change.docx"), chgOut, chg);
        String chgXml = docXml(chgOut.toPath());
        assertTrue(!chgXml.contains("{{"), "变更表渲染后不应残留占位符");
        assertTrue(chgXml.contains("√项目经费") && chgXml.contains("√执行周期"));
        assertTrue(!chgXml.contains("FF0000"), "变更表渲染值不得带红色");

        // 报告表:区块对循环分段
        RpHxFlow report = new RpHxFlow();
        report.setIsOnTime(1);
        report.setOutcomeForm("技术报告");
        report.setReportContent("一、目标达成情况\n\n【正文】完成。");
        Map<String, Object> rep = builder.buildReport(project(), hxProject(), report);
        File repOut = new File("target/test-task19-report.docx");
        tplSvc.renderDocx("hx_report", new File("../templates-src/hx_report.docx"), repOut, rep);
        String repXml = docXml(repOut.toPath());
        assertTrue(!repXml.contains("{{"), "报告表渲染后不应残留占位符(含区块对循环)");
        assertTrue(repXml.contains("一、目标达成情况") && repXml.contains("【正文】完成。"));
        assertTrue(repXml.contains("是√") && repXml.contains("√技术报告"));
        assertTrue(!repXml.contains("FF0000"), "报告表渲染值不得带红色");
    }

    @Test
    void reportBodyFillsWritingAreaPerParagraph() throws Exception {
        // 2026-10-08 修复:报告正文须落书写区——区块对跨段(poi-tl 按段循环)每段独立成段,
        // 不足 20 段剩余空行保留原版书写区高度,超出自然增段;正文段不得残留原版 28 字巨缩进
        TemplateService tplSvc = new TemplateService(null, null, "target/test-upload");

        RpHxFlow two = new RpHxFlow();
        two.setIsOnTime(1);
        two.setReportContent("一、目标达成情况\n\n二、研究过程总结");
        File out = new File("target/test-report-fill.docx");
        tplSvc.renderDocx("hx_report", new File("../templates-src/hx_report.docx"), out,
                builder.buildReport(project(), hxProject(), two));
        List<String> paras = paraTexts(out.toPath());
        int i1 = paras.indexOf("一、目标达成情况");
        int i2 = paras.indexOf("二、研究过程总结");
        assertTrue(i1 >= 0 && i2 == i1 + 1, "正文各段应各自成段且相邻相连,实际 i1=" + i1 + " i2=" + i2);
        assertTrue(!docXml(out.toPath()).contains("2800"), "正文段不得残留原版 firstLineChars=2800 巨缩进");
        int signIdx = -1;
        for (int i = 0; i < paras.size(); i++) {
            if (paras.get(i).contains("项目负责人（签字）")) {
                signIdx = i;
                break;
            }
        }
        assertTrue(signIdx > i2, "落款应在正文之后");
        long empties = paras.subList(i2 + 1, signIdx).stream().filter(String::isEmpty).count();
        assertTrue(empties >= 10, "内容不足时书写区空行应保留(实测剩余空段 " + empties + ")");

        // 超员:25 段各自成段,落款仍在最后(提示行明示页数不限)
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            sb.append("第").append(i).append("段正文内容,用于验证超员扩段。\n\n");
        }
        RpHxFlow many = new RpHxFlow();
        many.setIsOnTime(1);
        many.setReportContent(sb.toString());
        File out2 = new File("target/test-report-overflow.docx");
        tplSvc.renderDocx("hx_report", new File("../templates-src/hx_report.docx"), out2,
                builder.buildReport(project(), hxProject(), many));
        List<String> paras2 = paraTexts(out2.toPath());
        long bodyCount = paras2.stream().filter(s -> s.startsWith("第") && s.endsWith("。")).count();
        assertEquals(25, bodyCount, "超员 25 段应各自成段落入书写区");
        int signIdx2 = -1;
        for (int i = paras2.size() - 1; i >= 0; i--) {
            if (paras2.get(i).contains("年") && paras2.get(i).contains("月")) {
                signIdx2 = i;
                break;
            }
        }
        assertTrue(signIdx2 > 0, "应存在落款(年 月 日)段");
        assertTrue(paras2.subList(signIdx2 + 1, paras2.size()).stream().allMatch(String::isEmpty),
                "落款之后只允许原版文档收尾空段");
    }

    private static String docXml(Path docx) throws Exception {
        try (ZipFile z = new ZipFile(docx.toFile())) {
            return new String(z.getInputStream(z.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 全文档段落文字列表(按文档序,含表格内)——正文分段/空段保留断言用 */
    private static List<String> paraTexts(Path docx) throws Exception {
        byte[] xml;
        try (ZipFile z = new ZipFile(docx.toFile())) {
            xml = z.getInputStream(z.getEntry("word/document.xml")).readAllBytes();
        }
        var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true); // 默认 false 时 getElementsByTagNameNS 匹配不到 w: 前缀元素
        var doc = dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
        org.w3c.dom.NodeList ps = doc.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ps.getLength(); i++) {
            StringBuilder sb = new StringBuilder();
            org.w3c.dom.NodeList ts = ((org.w3c.dom.Element) ps.item(i)).getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t");
            for (int j = 0; j < ts.getLength(); j++) {
                sb.append(ts.item(j).getTextContent());
            }
            out.add(sb.toString());
        }
        return out;
    }
}
