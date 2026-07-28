package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
    @Select("SELECT c.*, m.name as medicine_name, m.image_url, m.price, m.drug_type, COALESCE(i.stock_quantity,0)-COALESCE(i.locked_quantity,0) as available_stock FROM cart_items c JOIN medicines m ON c.medicine_id=m.medicine_id LEFT JOIN inventory i ON m.medicine_id=i.medicine_id WHERE c.user_id=#{userId} ORDER BY c.created_at DESC")
    java.util.List<CartItem> selectByUserId(@Param("userId") Long userId);

    @Select("<script>SELECT c.*, m.name, m.price, m.drug_type, i.stock_quantity, i.locked_quantity FROM cart_items c JOIN medicines m ON c.medicine_id=m.medicine_id LEFT JOIN inventory i ON m.medicine_id=i.medicine_id WHERE c.cart_id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> AND c.user_id=#{userId} AND m.status=1</script>")
    java.util.List<CartItem> selectByIds(@Param("ids") java.util.List<Integer> ids, @Param("userId") Long userId);
}
