package com.fz.scitech.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/users")
@Validated
public class SysUserController {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SysUserController(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 当前登录用户信息(不含密码) */
    @GetMapping("/me")
    public Result<Map<String, String>> me(HttpServletRequest request) {
        return Result.ok(Map.of(
                "username", attr(request, "username"),
                "role", attr(request, "role")));
    }

    /** 修改自己的密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody ChangePwdReq req,
                                       HttpServletRequest request) {
        String username = attr(request, "username");
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null || !encoder.matches(req.oldPassword, user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(encoder.encode(req.newPassword));
        userMapper.updateById(update);
        return Result.ok();
    }

    private String attr(HttpServletRequest request, String name) {
        Object v = request.getAttribute(name);
        return v == null ? "" : v.toString();
    }

    @Data
    public static class ChangePwdReq {
        @NotBlank(message = "请输入原密码")
        private String oldPassword;

        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 32, message = "新密码长度需在 6~32 位之间")
        private String newPassword;
    }
}
