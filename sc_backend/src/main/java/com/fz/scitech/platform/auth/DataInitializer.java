package com.fz.scitech.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 开发环境默认账号(决策#35-④):仅 dev profile 生效,
 * prod 不创建任何默认账号(生产建正式账号+强密)。
 * dev 建号:admin/admin123(管理员)、teacher/teacher123(联调,上线停用);
 * teacher2/teacher2123(N3 Task20 教师间越权用例,幂等补插)。
 */
@Slf4j
@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) == 0) {
            insertUser("admin", "admin123", "系统管理员", "admin");
            insertUser("teacher", "teacher123", "测试教师", "teacher");
            log.info("[dev] 已创建默认账号:admin/admin123(管理员)、teacher/teacher123(测试教师)");
        }
        // 已初始化的库上首建块不会执行,teacher2 单独幂等补插(教师间越权联调用)
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "teacher2")) == 0) {
            insertUser("teacher2", "teacher2123", "测试教师乙", "teacher");
            log.info("[dev] 已补建联调账号:teacher2/teacher2123(测试教师乙)");
        }
    }

    private void insertUser(String username, String rawPassword, String realName, String role) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(encoder.encode(rawPassword));
        u.setRealName(realName);
        u.setRole(role);
        u.setStatus(1);
        u.setCreateBy("system");
        userMapper.insert(u);
    }
}
