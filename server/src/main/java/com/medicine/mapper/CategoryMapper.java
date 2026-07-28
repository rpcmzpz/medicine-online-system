package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    @Select("SELECT * FROM categories ORDER BY sort_order ASC")
    List<Category> selectAllOrdered();

    @Select("SELECT * FROM categories WHERE parent_id = #{parentId}")
    List<Category> selectByParentId(@Param("parentId") Integer parentId);
}
