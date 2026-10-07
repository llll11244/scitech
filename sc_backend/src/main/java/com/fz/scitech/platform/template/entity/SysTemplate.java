package com.fz.scitech.platform.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PDF 模板表:4 类模板的版本历史,启用版唯一(tpl_type+status=1 由服务层事务保证)
 */
@Data
@TableName("sys_template")
public class SysTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** hx_register/hx_change/hx_acceptance/hx_report */
    private String tplType;

    /** 版本号,递增 */
    private Integer version;

    /** upload/templates/ 下相对路径 */
    private String filePath;

    /** 上传时校验通过的占位符清单(JSON) */
    private String placeholderCheck;

    /** 1启用 0历史版本 */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String createBy;

    private Integer deleted;
}
