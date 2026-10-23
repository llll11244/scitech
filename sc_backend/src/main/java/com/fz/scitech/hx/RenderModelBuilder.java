package com.fz.scitech.hx;

import java.util.Arrays;

/**
 * 逗号多选串拆分+逐项 trim(变更审批/成果形式勾选命中与教师端校验同口径)。
 * 同名渲染建模型逻辑已随「HTML 即交付」与模板管理移除(2026-10-23),仅保留此工具。
 */
public final class RenderModelBuilder {
    private RenderModelBuilder() {}

    /** 逗号多选拆分+逐项 trim(null→空数组):防「项目经费, 执行周期」带空格导致勾选不命中(HxService 校验同口径) */
    static String[] splitTrim(String s) {
        return s == null ? new String[0]
                : Arrays.stream(s.split(",")).map(String::trim).toArray(String[]::new);
    }
}
