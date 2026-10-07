package com.fz.scitech.platform.auth;

import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.service.SysUserService;
import com.fz.scitech.system.service.dto.LoginReq;
import com.fz.scitech.system.service.dto.LoginVO;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录(含失败锁定,决策#35-⑤)。
 * 用户不存在与密码错误给同一提示,避免账号探测。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysUserService userService;
    private final JwtUtil jwtUtil;
    private final LoginGuard loginGuard;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(SysUserService userService, JwtUtil jwtUtil, LoginGuard loginGuard) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.loginGuard = loginGuard;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginReq req) {
        loginGuard.checkLocked(req.getUsername());

        SysUser user = userService.getByUsername(req.getUsername());
        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            loginGuard.recordFail(req.getUsername());
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用,请联系管理员");
        }
        loginGuard.reset(req.getUsername());

        return Result.ok(LoginVO.builder()
                .token(jwtUtil.create(user.getId(), user.getUsername(), user.getRole()))
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .build());
    }
}
