local node, cursor = ARGV[1], ARGV[2]
local lease = tonumber(ARGV[3])
if #node == 0 or #node > 384 or not string.match(cursor, "^%d+%-%d+$") then
    return redis.error_reply("AXS_STREAM_INVALID_READER")
end
local time = redis.call("TIME")
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local expired = redis.call("ZRANGEBYSCORE", KEYS[5], "-inf", now)
for _, reader in ipairs(expired) do
    redis.call("HDEL", KEYS[4], reader)
    redis.call("ZREM", KEYS[5], reader)
end
if redis.call("HEXISTS", KEYS[4], node) == 0 and redis.call("ZCARD", KEYS[5]) >= 256 then
    return redis.error_reply("AXS_STREAM_READER_LIMIT")
end
redis.call("HSET", KEYS[4], node, cursor)
redis.call("ZADD", KEYS[5], now + lease, node)
-- Do not let idle stream keys expire while readers are still protected.
for i = 1, 5 do
    local ttl = redis.call("PTTL", KEYS[i])
    if ttl >= 0 and ttl < lease + 60000 then
        redis.call("PEXPIRE", KEYS[i], lease + 60000)
    elseif ttl == -1 and i >= 4 then
        redis.call("PEXPIRE", KEYS[i], lease + 60000)
    end
end
return 1
