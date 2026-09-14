package com.medicine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Redis 相关配置。
 *
 * <p>Lua 脚本统一在这里注册成 Bean：脚本由 Redis 服务端原子执行，
 * 避免「读-判断-写」这三步被并发请求穿插，这是分布式锁和库存扣减正确性的前提。
 */
@Configuration
public class RedisConfig {

    /** 释放分布式锁：value 相等才删除，防止误删他人的锁 */
    @Bean("unlockScript")
    public DefaultRedisScript<Long> unlockScript() {
        return loadLongScript("lua/unlock.lua");
    }

    /** 库存原子预减 */
    @Bean("deductStockScript")
    public DefaultRedisScript<Long> deductStockScript() {
        return loadLongScript("lua/deduct_stock.lua");
    }

    /** 库存预减回滚 */
    @Bean("rollbackStockScript")
    public DefaultRedisScript<Long> rollbackStockScript() {
        return loadLongScript("lua/rollback_stock.lua");
    }

    private DefaultRedisScript<Long> loadLongScript(String classpathLocation) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(read(classpathLocation));
        return script;
    }

    private String read(String classpathLocation) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpathLocation).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new IllegalStateException("加载 Redis Lua 脚本失败: " + classpathLocation, e);
        }
    }
}
