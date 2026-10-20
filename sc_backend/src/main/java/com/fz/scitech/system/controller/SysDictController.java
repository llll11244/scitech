package com.fz.scitech.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysDict;
import com.fz.scitech.system.mapper.SysDictMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据字典:下拉框数据来源+管理端维护(N4 Task26 补齐改/停/删,spec §7.2)。
 * 内置值保护:is_system=1 不可删、label 不可改(仅可停用/启用/调序)——图表与既有数据依赖取值。
 * 模板联动字典(dict_type ∈ TEMPLATE_LINKED)取值已印在制式 PDF 模板勾选组/科目行:
 * 增/删响应体带 remind 字段(前端弹强提醒:同步更新模板并重新上传)。
 */
@RestController
@RequestMapping("/dicts")
@Validated
public class SysDictController {

    /** 模板联动字典类型(增删取值须提醒同步改模板;2026-10-19 决策#32 前缀对齐改名) */
    private static final Set<String> TEMPLATE_LINKED = Set.of(
            "hx_project_type", "hx_client_nature", "hx_outcome_form", "hx_change_type", "hx_budget_subject");
    private static final String REMIND_TEXT = "该字典取值已印在 PDF 模板勾选组/科目行,请同步更新模板并重新上传";

    private final SysDictMapper dictMapper;

    public SysDictController(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    /** 按类型取启用的字典项,sort_order 升序(所有登录角色可用,下拉框数据源) */
    @GetMapping
    public Result<List<SysDict>> list(@RequestParam String type) {
        return Result.ok(dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSortOrder)));
    }

    /** 管理端全量取值(含停用项,仅 admin;type 空=全部类型——字典管理页 tab 数据源) */
    @GetMapping("/manage")
    public Result<List<SysDict>> manage(@RequestParam(required = false) String type,
                                        HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(type != null && !type.isBlank(), SysDict::getDictType, type)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSortOrder)
                .orderByAsc(SysDict::getId)));
    }

    /** 管理员新增字典项(联动类型响应带 remind=模板同步提醒) */
    @PostMapping
    public Result<Map<String, Object>> add(@RequestParam @NotBlank(message = "类型不能为空") String type,
                                           @RequestParam @NotBlank(message = "取值不能为空") String value,
                                           @RequestParam @NotBlank(message = "显示名不能为空") String label,
                                           HttpServletRequest request) {
        requireAdmin(request);
        Long exists = dictMapper.selectCount(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, type).eq(SysDict::getDictValue, value));
        if (exists > 0) {
            throw new BizException("该字典项已存在");
        }
        SysDict d = new SysDict();
        d.setDictType(type);
        d.setDictValue(value);
        d.setDictLabel(label);
        d.setIsSystem(0);
        d.setSortOrder(99);
        d.setStatus(1);
        d.setCreateBy(attr(request));
        try {
            dictMapper.insert(d);
        } catch (DuplicateKeyException e) {
            // 唯一键兜底:预检查不到的行(历史逻辑删行占键/并发插入)给友好提示
            throw new BizException("该字典取值已存在(含历史已删取值),请更换取值或先用管理页删除旧值");
        }
        return Result.ok(remindOf(type));
    }

    /** 管理员改 label/sortOrder/status(is_system=1 时 label 不可改;label 非空必填) */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictUpdateBody body,
                               HttpServletRequest request) {
        requireAdmin(request);
        SysDict d = requireDict(id);
        boolean labelChanged = body.label != null && !body.label.isBlank()
                && !body.label.equals(d.getDictLabel());
        if (labelChanged && d.getIsSystem() != null && d.getIsSystem() == 1) {
            throw new BizException("系统内置取值的显示名不可修改(可停用/启用/调序)");
        }
        if (body.label != null && body.label.isBlank()) {
            throw new BizException("显示名不能为空");
        }
        if (body.status != null && body.status != 0 && body.status != 1) {
            throw new BizException("status 仅支持 0(停用)/1(启用)");
        }
        SysDict upd = new SysDict();
        upd.setId(id);
        if (labelChanged) {
            upd.setDictLabel(body.label.trim());
        }
        if (body.sortOrder != null) {
            upd.setSortOrder(body.sortOrder);
        }
        if (body.status != null) {
            upd.setStatus(body.status);
        }
        dictMapper.updateById(upd);
        return Result.ok();
    }

    /** 管理员删字典项(is_system=1 拒;联动类型响应带 remind) */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        SysDict d = requireDict(id);
        if (d.getIsSystem() != null && d.getIsSystem() == 1) {
            throw new BizException("系统内置取值不可删除(可停用)");
        }
        // 物理删:逻辑删行仍占 uk_type_value 唯一键,同取值重插会撞索引(见 SysDictMapper)
        dictMapper.physicalDeleteById(id);
        return Result.ok(remindOf(d.getDictType()));
    }

    // ---------- 通用 ----------
    /** 联动字典提醒载荷:非联动类型返回空 Map(响应结构稳定,前端按 remind 有无弹窗) */
    private static Map<String, Object> remindOf(String dictType) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (TEMPLATE_LINKED.contains(dictType)) {
            m.put("remind", REMIND_TEXT);
        }
        return m;
    }

    private SysDict requireDict(Long id) {
        SysDict d = dictMapper.selectById(id);
        if (d == null) {
            throw new BizException("字典项不存在");
        }
        return d;
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            throw new BizException("仅管理员可操作");
        }
    }

    private String attr(HttpServletRequest request) {
        Object v = request.getAttribute("username");
        return v == null ? null : v.toString();
    }

    /** 字典项修改载荷(全可空=不改;label 空串视为清空→拒绝) */
    @Data
    public static class DictUpdateBody {
        private String label;
        private Integer sortOrder;
        private Integer status;
    }
}
