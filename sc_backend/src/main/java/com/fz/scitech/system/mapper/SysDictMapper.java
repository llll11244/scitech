package com.fz.scitech.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fz.scitech.system.entity.SysDict;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SysDictMapper extends BaseMapper<SysDict> {

    /**
     * 物理删字典行(Task26)。uk_type_value(dict_type,dict_value) 唯一键与逻辑删互斥:
     * 逻辑删行仍占键,同取值重插必撞唯一索引(与 RpBudgetMapper 同口径)——
     * 字典取值是配置数据非审计对象,删除即真删,取值此后可重新新增。
     */
    @Delete("DELETE FROM sys_dict WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
