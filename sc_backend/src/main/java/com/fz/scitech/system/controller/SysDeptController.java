package com.fz.scitech.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysDept;
import com.fz.scitech.system.mapper.SysDeptMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/depts")
@Validated
public class SysDeptController {

    private final SysDeptMapper deptMapper;

    public SysDeptController(SysDeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    /** 院部列表(平铺,按 sort_order) */
    @GetMapping
    public Result<List<SysDept>> list() {
        return Result.ok(deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                .orderByAsc(SysDept::getSortOrder).orderByAsc(SysDept::getId)));
    }

    /** 管理员新增院部 */
    @PostMapping
    public Result<Void> add(@RequestBody @NotBlank(message = "名称不能为空") String name,
                            HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            throw new BizException("仅管理员可操作");
        }
        Long exists = deptMapper.selectCount(Wrappers.<SysDept>lambdaQuery()
                .eq(SysDept::getName, name));
        if (exists > 0) {
            throw new BizException("该院部已存在");
        }
        SysDept d = new SysDept();
        d.setName(name);
        d.setParentId(0L);
        d.setSortOrder(99);
        d.setCreateBy(String.valueOf(request.getAttribute("username")));
        deptMapper.insert(d);
        return Result.ok();
    }
}
