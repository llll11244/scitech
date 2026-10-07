package com.fz.scitech.platform.attachment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目附件表(平台级,决策#11/32):存储相对路径(sc.upload-dir 可配,位置可迁移);
 * 逻辑删仅标记,磁盘文件保留(决策#34-⑧)。
 * biz_type: hx_apply申报佐证/hx_scan盖章扫描件/hx_final终版数据PDF/hx_pdf过程待签PDF(含作废版)/hx_extra验收另附材料
 */
@Data
@TableName("rp_attachment")
public class RpAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** hx_apply/hx_scan/hx_final/hx_pdf/hx_extra */
    private String bizType;

    /** 文档类型(字典 hx_scan_doc_type:登记表/变更表/验收登记表/验收报告;另含"合同") */
    private String docType;

    private String fileName;

    /** 存储相对路径(相对上传根目录) */
    private String filePath;

    /** 字节 */
    private Long fileSize;

    private Long uploaderId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String createBy;

    private Integer deleted;
}
