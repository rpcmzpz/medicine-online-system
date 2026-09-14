-- 预减回滚（下单失败补偿 / 订单取消 / 处方驳回）。
-- 只在 key 仍存在时回补：如果 key 已被删除（失效），直接 incrby 会把库存凭空造出来，
-- 得到一个与数据库完全不符的错误计数。
if redis.call('exists', KEYS[1]) == 1 then
    return redis.call('incrby', KEYS[1], ARGV[1])
end
return -1
