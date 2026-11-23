package com.medicine.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 *
 * 为什么必须有它：实体上写了 `@TableField(fill = FieldFill.INSERT)` 的字段，
 * MyBatis-Plus 在生成 INSERT 语句时**一定**会带上这一列（不再做 null 判断）。
 * 如果容器里没有 MetaObjectHandler，这一列就会被显式写成 NULL，
 * 从而覆盖掉数据库的 `DEFAULT CURRENT_TIMESTAMP`——表现为创建时间永远为 null。
 *
 * strictInsertFill 只在字段存在且当前为 null 时填充，不会覆盖业务代码已设的值。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
