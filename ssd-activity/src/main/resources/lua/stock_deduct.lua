-- KEYS[1] 库存键
-- KEYS[2] 幂等键
-- ARGV[1] TTL 秒
-- ARGV[2] 首次成功写入的结果载荷
-- 返回 {code, payload}  code: 1 成功 / 0 库存不足 / -1 重复
local idem = redis.call('GET', KEYS[2])
if idem then
  return {-1, idem}
end
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
  return {0, ''}
end
redis.call('DECR', KEYS[1])
redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[1]))
return {1, ARGV[2]}
