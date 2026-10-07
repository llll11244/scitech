package com.fz.scitech.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysUserMapper;
import com.fz.scitech.system.service.SysUserService;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;

    public SysUserServiceImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public SysUser getByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
    }

    @Override
    public SysUser getById(Long id) {
        return userMapper.selectById(id);
    }
}
