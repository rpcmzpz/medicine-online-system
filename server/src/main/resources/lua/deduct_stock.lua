-- 库存原子预减。
-- 返回值： -1 未预热（调用方降级为纯 DB 校验）
--          -2 库存不足（可直接快速失败，不必查库）
--          >=0 扣减后的剩余库存
-- 「读库存 - 判断 - 扣减」三步必须在服务端一次原子完成，否则并发下仍会超卖。
local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil then
    return -1
end

local quantity = tonumber(ARGV[1])
if quantity == nil or quantity <= 0 then
    return stock
end

if stock < quantity then
    return -2
end

redis.call('decrby', KEYS[1], quantity)
return stock - quantity
