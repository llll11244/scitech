package com.fz.scitech.system.service;

import com.fz.scitech.system.entity.SysUser;

/**
 * 用户查询服务。
 * 登录逻辑归 platform/auth(Task 5,含失败锁定);本服务提供
 * 拦截器与各模块共用的用户查询能力。
 */
public interface SysUserService {

    /** 按用户名查(登录校验/判重) */
    SysUser getByUsername(String username);

    /** 按主键查(拦截器每请求一次,带出最新角色与状态,决策#35-②) */
    SysUser getById(Long id);
}
