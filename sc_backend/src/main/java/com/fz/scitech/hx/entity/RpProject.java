package com.fz.scitech.hx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目公共主表(rp_project,15 列最小真公共,决策#27/#30):横向 kind=2。
 * deptName/leaderPhone 非表列(院部名/负责人电话在 sys_dept/sys_user),由 HxService 查询填充。
 */
@Data
@TableName("rp_project")
public class RpProject {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 1纵向(预留) 2横向 */
    private Integer kind;
    /** 0草稿/1已提交/2已确认(进台账)/3退回 */
    private Integer auditStatus;
    /** 归属教师→sys_user.id */
    private Long applyUserId;
    /** 全局唯一对外编号;横向=HX{年}-{序} */
    private String approvalNo;
    private String name;
    private String leaderName;
    /** 院部→sys_dept.id */
    private Long deptId;
    /** 生命周期大状态(字典);横向仅用 立项/结题 */
    private String status;
    /** 流程发起日(横向登记提交日) */
    private LocalDate declareDate;
    /** 完结日(横向验收确认日) */
    private LocalDate finishDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    /** 逻辑删除(yml 全局 logic-delete-field,无需 @TableLogic) */
    private Integer deleted;

    /** 院部名称(sys_dept.name,HxService 按 dept_id 填充) */
    @TableField(exist = false)
    private String deptName;
    /** 负责人电话(sys_user.phone,HxService 填充;登记表 leaderPhone 占位符数据源) */
    @TableField(exist = false)
    private String leaderPhone;
}
