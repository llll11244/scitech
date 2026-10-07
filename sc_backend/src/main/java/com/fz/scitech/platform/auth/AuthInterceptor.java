package com.fz.scitech.platform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.service.SysUserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验(决策#35-②):请求头 Authorization: Bearer <token>
 * 解析后按 uid 主键查 sys_user(一次主键 SELECT,校内部署可接受):
 * 账号不存在/已停用 → 立即 401(替代吊销黑名单);
 * 通过则注入 uid/username/role —— role 取库中最新值,非 token 快照。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(JwtUtil jwtUtil, SysUserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, "未登录");
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            Long uid = Long.parseLong(claims.get("uid", String.class));
            SysUser user = userService.getById(uid);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                return reject(response, "账号已停用或已删除,请联系管理员");
            }
            request.setAttribute("uid", user.getId());
            request.setAttribute("username", user.getUsername());
            request.setAttribute("role", user.getRole());
            return true;
        } catch (NumberFormatException e) {
            return reject(response, "登录凭证无效,请重新登录");
        } catch (Exception e) {
            return reject(response, "登录已过期,请重新登录");
        }
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, msg)));
        return false;
    }
}
