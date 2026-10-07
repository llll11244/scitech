package com.fz.scitech.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fz.scitech.common.BizException;
import com.fz.scitech.common.Result;
import com.fz.scitech.system.entity.SysNotify;
import com.fz.scitech.system.entity.SysUser;
import com.fz.scitech.system.mapper.SysNotifyMapper;
import com.fz.scitech.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息:我的消息 / 未读数 / 标记已读。
 */
@RestController
@RequestMapping("/notify")
public class SysNotifyController {

    private final SysNotifyMapper notifyMapper;
    private final SysUserMapper userMapper;

    public SysNotifyController(SysNotifyMapper notifyMapper, SysUserMapper userMapper) {
        this.notifyMapper = notifyMapper;
        this.userMapper = userMapper;
    }

    /** 我的消息(倒序分页) */
    @GetMapping("/my")
    public Result<Page<SysNotify>> my(@RequestParam(defaultValue = "1") int current,
                                      @RequestParam(defaultValue = "10") int size,
                                      HttpServletRequest request) {
        SysUser me = currentUser(request);
        return Result.ok(notifyMapper.selectPage(new Page<>(current, size),
                Wrappers.<SysNotify>lambdaQuery()
                        .eq(SysNotify::getUserId, me.getId())
                        .orderByDesc(SysNotify::getId)));
    }

    /** 未读条数(顶部角标轮询) */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(HttpServletRequest request) {
        SysUser me = currentUser(request);
        Long c = notifyMapper.selectCount(Wrappers.<SysNotify>lambdaQuery()
                .eq(SysNotify::getUserId, me.getId()).eq(SysNotify::getIsRead, 0));
        return Result.ok(c == null ? 0L : c);
    }

    /** 标记单条已读(仅自己的) */
    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id, HttpServletRequest request) {
        SysUser me = currentUser(request);
        SysNotify n = notifyMapper.selectById(id);
        if (n == null || !n.getUserId().equals(me.getId())) throw new BizException("消息不存在");
        if (n.getIsRead() == 0) {
            notifyMapper.update(null, Wrappers.<SysNotify>lambdaUpdate()
                    .eq(SysNotify::getId, id).set(SysNotify::getIsRead, 1));
        }
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Result<Void> readAll(HttpServletRequest request) {
        SysUser me = currentUser(request);
        notifyMapper.update(null, Wrappers.<SysNotify>lambdaUpdate()
                .eq(SysNotify::getUserId, me.getId()).eq(SysNotify::getIsRead, 0)
                .set(SysNotify::getIsRead, 1));
        return Result.ok();
    }

    private SysUser currentUser(HttpServletRequest request) {
        String username = String.valueOf(request.getAttribute("username"));
        SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (u == null) throw new BizException("账号不存在");
        return u;
    }
}
