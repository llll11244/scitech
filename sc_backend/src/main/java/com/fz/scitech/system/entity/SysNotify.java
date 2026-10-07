package com.fz.scitech.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息表(审核结果通知等)。
 */
@Data
@TableName("sys_notify")
public class SysNotify {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人→sys_user.id */
    private Long userId;

    private String title;

    private String content;

    /** 跳转关联项目 */
    private Long projectId;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String createBy;

    @TableLogic
    private Integer deleted;
}
