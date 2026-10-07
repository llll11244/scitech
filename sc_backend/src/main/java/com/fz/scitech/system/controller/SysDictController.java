package com.fz.scitech.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysDict;
import com.fz.scitech.system.mapper.SysDictMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据字典:下拉框数据来源。
 * 管理端增删改界面阶段3后补,当前提供查询+管理员新增。
 */
@RestController
@RequestMapping("/dicts")
@Validated
public class SysDictController {

    private final SysDictMapper dictMapper;

    public SysDictController(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    /** 按类型取启用的字典项,sort_order 升序 */
    @GetMapping
    public Result<List<SysDict>> list(@RequestParam String type) {
        return Result.ok(dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSortOrder)));
    }

    /** 管理员新增字典项 */
    @PostMapping
    public Result<Void> add(@RequestParam @NotBlank(message = "类型不能为空") String type,
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
        dictMapper.insert(d);
        return Result.ok();
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
}
