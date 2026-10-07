package com.fz.scitech.platform.template;

import java.util.Map;

/**
 * 渲染模型构造器 SPI(决策#32 平台层扩展点):业务模块(hx/zs…)实现本接口,
 * 把流程单数据组装为模板模型(勾选组预拼/大写金额/万元换算/yyyy年MM月日期在此层完成;
 * 勾选组约定 √/□,勿用 ☑/■——宋体字形坑见 CLAUDE.md 踩坑#6,拼法参照 SampleData.checks)。
 * 平台渲染管线按 tplType 路由到对应实现;样例实现见 SampleData.forType(仅测试/预览用)。
 */
public interface RenderModelBuilder {

    /** 本实现负责的模板类型:hx_register/hx_change/hx_acceptance/hx_report(纵向模块将来新增) */
    String tplType();

    /** 按流程单主键组装渲染模型 */
    Map<String, Object> build(Long flowId);
}
