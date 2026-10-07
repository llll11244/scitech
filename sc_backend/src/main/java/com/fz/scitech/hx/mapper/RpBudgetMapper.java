package com.fz.scitech.hx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fz.scitech.hx.entity.RpBudget;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/** 项目预算明细表 Mapper(@MapperScan 扫描,同 system/platform 模块惯例) */
public interface RpBudgetMapper extends BaseMapper<RpBudget> {

    /**
     * 物理清空项目预算行(草稿重存的替换语义)。
     * uk_project_subject(project_id,subject) 唯一键与逻辑删互斥:逻辑删行仍占键,
     * 同科目重插必撞唯一索引——预算行是全量替换的派生数据,非审计对象
     * (决策#34-⑧"文件保留"仅约束附件),重存必须真删。
     */
    @Delete("DELETE FROM rp_budget WHERE project_id = #{projectId}")
    int physicalDeleteByProject(@Param("projectId") Long projectId);
}
