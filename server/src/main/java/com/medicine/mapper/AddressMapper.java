package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AddressMapper extends BaseMapper<Address> {
    @Update("UPDATE addresses SET is_default = 1 WHERE address_id = #{addressId} AND user_id = #{userId}")
    int setDefault(@Param("addressId") Long addressId, @Param("userId") Long userId);

    @Update("UPDATE addresses SET is_default = 0 WHERE user_id = #{userId}")
    int clearDefault(@Param("userId") Long userId);
}
