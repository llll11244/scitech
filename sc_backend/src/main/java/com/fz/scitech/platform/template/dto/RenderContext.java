package com.fz.scitech.platform.template.dto;

/**
 * 渲染上下文:一次流程单渲染的定位信息。
 * flowType=业务流程(register/change/acceptance);tplType=模板类型(hx_report 亦属 acceptance 流程)。
 */
public record RenderContext(
        Long flowId,
        Long projectId,
        String flowType,
        String tplType,
        Long operatorId,
        String operatorName,
        boolean finalVersion
) {
}
