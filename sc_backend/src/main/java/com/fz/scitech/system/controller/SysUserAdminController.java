package com.fz.scitech.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysDept;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysDeptMapper;
import com.fz.scitech.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 最小用户管理(N4 Task26,仅 admin;计划裁定:建号/停启/重置密码,无删)。
 * 建号缺口=变更负责人「须已有账号」依赖现场建号(spec 待确认#2)。
 * 停用语义沿用决策#35:AuthInterceptor 每请求按 uid 查库,停用立即 401;
 * 不可停用自己(自锁防线);密码强度≥8 位且含字母与数字(弱口令防线)。
 */
@RestController
@RequestMapping("/users")
@Validated
public class SysUserAdminController {

    /** 合法角色(与 sys_user.role 注释口径一致) */
    private static final Set<String> ROLES = Set.of("admin", "viewer", "teacher");
    /** 密码强度:≥8 位且同时含字母与数字 */
    private static final Pattern PWD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,32}$");

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SysUserAdminController(SysUserMapper userMapper, SysDeptMapper deptMapper) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
    }

    /** 用户分页列表(仅 admin):q=用户名/姓名模糊;绝不含 password 列 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String q,
                                            HttpServletRequest request) {
        requireAdmin(request);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .and(q != null && !q.isBlank(), w -> w
                                .like(SysUser::getUsername, q.trim()).or().like(SysUser::getRealName, q.trim()))
                        .orderByAsc(SysUser::getId));
        List<Long> deptIds = p.getRecords().stream().map(SysUser::getDeptId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(deptIds).stream()
                        .collect(java.util.stream.Collectors.toMap(SysDept::getId, SysDept::getName));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser u : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("role", u.getRole());
            m.put("status", u.getStatus());
            m.put("deptName", u.getDeptId() == null ? null : deptNames.get(u.getDeptId()));
            m.put("phone", u.getPhone());
            m.put("lastLogin", u.getLastLogin());
            m.put("createTime", u.getCreateTime());
            rows.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", rows);
        data.put("total", p.getTotal());
        return Result.ok(data);
    }

    /** 建号(admin):用户名唯一;密码≥8 位含字母与数字;role∈admin/viewer/teacher */
    @PostMapping
    public Result<Long> create(@Validated @RequestBody CreateUserBody body, HttpServletRequest request) {
        requireAdmin(request);
        if (body.role == null || !ROLES.contains(body.role)) {
            throw new BizException("role 仅支持 admin/viewer/teacher");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, body.username)) > 0) {
            throw new BizException("用户名已存在");
        }
        strength(body.password);
        SysUser u = new SysUser();
        u.setUsername(body.username);
        u.setPassword(encoder.encode(body.password));
        u.setRealName(body.realName);
        u.setRole(body.role);
        u.setDeptId(body.deptId);
        u.setPhone(body.phone);
        u.setStatus(1);
        u.setCreateBy(attr(request));
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            // uk_username 兜底:并发双击下 selectCount 判重窗口外的插入冲突给友好提示(同 SysDictController 口径)
            throw new BizException("用户名已存在,请更换用户名");
        }
        return Result.ok(u.getId());
    }

    /** 启停(admin):停用即时生效(拦截器按 uid 查库,决策#35);不可停用自己 */
    @PutMapping("/{id}/status")
    public Result<Void> status(@PathVariable Long id, @RequestBody StatusBody body, HttpServletRequest request) {
        requireAdmin(request);
        if (body.status == null || (body.status != 0 && body.status != 1)) {
            throw new BizException("status 仅支持 0(停用)/1(启用)");
        }
        SysUser u = requireUser(id);
        if (body.status == 0 && Objects.equals(u.getId(), uid(request))) {
            throw new BizException("不可停用当前登录账号自己");
        }
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id).set(SysUser::getStatus, body.status));
        return Result.ok();
    }

    /** 重置密码(admin):同建号强度口径;既有 token 不受影响(拦截器只验状态) */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPwdBody body,
                                      HttpServletRequest request) {
        requireAdmin(request);
        requireUser(id);
        strength(body.password);
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id).set(SysUser::getPassword, encoder.encode(body.password)));
        return Result.ok();
    }

    // ---------- 通用 ----------
    private void strength(String password) {
        if (password == null || !PWD.matcher(password).matches()) {
            throw new BizException("密码须 8~32 位且同时包含字母与数字");
        }
    }

    private SysUser requireUser(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        return u;
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            throw new BizException("仅管理员可操作");
        }
    }

    private Long uid(HttpServletRequest request) {
        return (Long) request.getAttribute("uid");
    }

    private String attr(HttpServletRequest request) {
        Object v = request.getAttribute("username");
        return v == null ? null : v.toString();
    }

    /** 建号载荷 */
    @Data
    public static class CreateUserBody {
        @NotBlank(message = "用户名必填")
        private String username;
        @NotBlank(message = "姓名必填")
        private String realName;
        @NotBlank(message = "角色必填")
        private String role;
        @NotBlank(message = "密码必填")
        private String password;
        private Long deptId;
        private String phone;
    }

    @Data
    public static class StatusBody {
        private Integer status;
    }

    @Data
    public static class ResetPwdBody {
        @NotBlank(message = "新密码必填")
        private String password;
    }
}
