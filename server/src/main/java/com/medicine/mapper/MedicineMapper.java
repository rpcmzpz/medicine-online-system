package com.medicine.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface MedicineMapper extends BaseMapper<Medicine> {
    @Select("SELECT m.*, i.stock_quantity, i.locked_quantity, COALESCE(i.stock_quantity,0)-COALESCE(i.locked_quantity,0) as available_stock, c.name as category_name FROM medicines m LEFT JOIN inventory i ON m.medicine_id=i.medicine_id LEFT JOIN categories c ON m.category_id=c.category_id WHERE m.medicine_id=#{id}")
    Medicine selectDetailById(@Param("id") Long id);

    @Select("SELECT AVG(rating) as avg_rating, COUNT(*) as count FROM reviews WHERE medicine_id=#{medicineId}")
    Map<String, Object> selectAvgRating(@Param("medicineId") Long medicineId);

    @Select("<script>" +
        "SELECT m.*, COALESCE(i.stock_quantity,0)-COALESCE(i.locked_quantity,0) as available_stock " +
        "FROM medicines m LEFT JOIN inventory i ON m.medicine_id = i.medicine_id " +
        "WHERE m.status = 1 " +
        "<if test='keyword != null and keyword != \"\"'>" +
        "AND (m.name LIKE CONCAT('%',#{keyword},'%') OR m.generic_name LIKE CONCAT('%',#{keyword},'%') OR m.brand LIKE CONCAT('%',#{keyword},'%')) " +
        "</if>" +
        "<if test='categoryIds != null and categoryIds.size() > 0'>" +
        "AND m.category_id IN <foreach item='id' collection='categoryIds' open='(' separator=',' close=')'>#{id}</foreach> " +
        "</if>" +
        "<if test='drugType != null'>" +
        "AND m.drug_type = #{drugType} " +
        "</if>" +
        "<choose>" +
        "<when test=\"sortBy == 'price_asc'\">ORDER BY m.price ASC</when>" +
        "<when test=\"sortBy == 'price_desc'\">ORDER BY m.price DESC</when>" +
        "<when test=\"sortBy == 'sales_desc'\">ORDER BY m.sales_count DESC</when>" +
        "<otherwise>ORDER BY m.created_at DESC</otherwise>" +
        "</choose>" +
        "LIMIT #{offset}, #{limit}" +
        "</script>")
    List<Map<String, Object>> selectListWithStock(@Param("keyword") String keyword, @Param("categoryIds") List<Integer> categoryIds, @Param("drugType") Integer drugType, @Param("sortBy") String sortBy, @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>" +
        "SELECT COUNT(*) as total FROM medicines m WHERE m.status = 1 " +
        "<if test='keyword != null and keyword != \"\"'>" +
        "AND (m.name LIKE CONCAT('%',#{keyword},'%') OR m.generic_name LIKE CONCAT('%',#{keyword},'%') OR m.brand LIKE CONCAT('%',#{keyword},'%')) " +
        "</if>" +
        "<if test='categoryIds != null and categoryIds.size() > 0'>" +
        "AND m.category_id IN <foreach item='id' collection='categoryIds' open='(' separator=',' close=')'>#{id}</foreach> " +
        "</if>" +
        "<if test='drugType != null'>" +
        "AND m.drug_type = #{drugType} " +
        "</if>" +
        "</script>")
    int countListWithStock(@Param("keyword") String keyword, @Param("categoryIds") List<Integer> categoryIds, @Param("drugType") Integer drugType);

    @Select("<script>" +
        "SELECT m.*, COALESCE(i.stock_quantity,0) as stock_quantity, COALESCE(i.locked_quantity,0) as locked_quantity, COALESCE(i.stock_quantity,0)-COALESCE(i.locked_quantity,0) as available_stock " +
        "FROM medicines m LEFT JOIN inventory i ON m.medicine_id = i.medicine_id " +
        "WHERE 1=1 ${ew.customSqlSegment}" +
        "</script>")
    List<Medicine> selectWithInventory(@Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<Medicine> wrapper);

    @Select("<script>" +
        "SELECT COUNT(*) FROM medicines m " +
        "<where> ${ew.sqlSegment} </where>" +
        "</script>")
    long countWithInventory(@Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<Medicine> wrapper);
}
