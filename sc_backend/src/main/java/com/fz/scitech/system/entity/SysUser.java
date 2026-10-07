package com.fz.scitech.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文,任何接口不得返回该字段 */
    private String password;

    private String realName;

    /** admin 管理员 / viewer 只读访客 / teacher 教师 */
    private String role;

    private Long deptId;

    private String phone;

    /** 职称(字典 title),教师资料 */
    private String title;

    /** 学位(字典 degree),教师资料 */
    private String degree;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String createBy;

    @TableLogic
    private Integer deleted;
}
