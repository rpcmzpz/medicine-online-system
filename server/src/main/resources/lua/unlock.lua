-- 释放分布式锁：只有 value 与自己持有的 token 相等才删除。
-- 否则会出现「A 的锁已超时自动释放、B 拿到锁，A 执行完却把 B 的锁删掉」的误删。
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
