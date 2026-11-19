local retention = tonumber(ARGV[2])
local max_bytes = tonumber(ARGV[3])
local frame_limit = math.min(tonumber(ARGV[4]), max_bytes)
if #ARGV > 35 then
    return redis.error_reply("AXS_STREAM_BATCH_TOO_LARGE")
end
if #ARGV[1] > frame_limit then return redis.error_reply("AXS_STREAM_FRAME_TOO_LARGE") end
for i = 5, #ARGV do
    if #ARGV[i] > frame_limit then return redis.error_reply("AXS_STREAM_FRAME_TOO_LARGE") end
end

local function append(wire)

local bytes = tonumber(redis.call("HGET", KEYS[3], "bytes") or "0")
local previous = redis.call("HGET", KEYS[3], "last") or "0-0"
local count = redis.call("LLEN", KEYS[2])
if redis.call("EXISTS", KEYS[1]) == 0 then
    redis.call("DEL", KEYS[2], KEYS[3])
    bytes = 0
    previous = "0-0"
    count = 0
elseif previous == "0-0" or (count == 0 and (bytes ~= 0 or redis.call("XLEN", KEYS[1]) ~= 0)) then
    return redis.error_reply("AXS_STREAM_INDEX_MISSING")
end

local function less(a, b)
    local at, as = string.match(a, "^(%d+)%-(%d+)$")
    local bt, bs = string.match(b, "^(%d+)%-(%d+)$")
    if at ~= bt then
        if #at ~= #bt then return #at < #bt end
        return at < bt
    end
    if #as ~= #bs then return #as < #bs end
    return as < bs
end

local time = redis.call("TIME")
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local cutoff = now - retention
local oldest = redis.call("LINDEX", KEYS[2], 0)
local trimmed = false
local floor = nil
local loaded_floor = false
while count > 0 do
    local oldest_id, size = string.match(oldest, "^([^:]+):(%d+)$")
    if bytes + #wire <= max_bytes and count < 65536
            and tonumber(string.match(oldest_id, "^%d+")) >= cutoff then
        break
    end
    if not loaded_floor then
        local readers = redis.call("ZRANGEBYSCORE", KEYS[5], "(" .. now, "+inf")
        for _, reader in ipairs(readers) do
            local cursor = redis.call("HGET", KEYS[4], reader) or "0-0"
            if not floor or less(cursor, floor) then floor = cursor end
        end
        loaded_floor = true
    end
    -- A live (or briefly disconnected) reader still needs this entry.
    if floor and less(floor, oldest_id) then break end
    bytes = bytes - tonumber(size)
    count = count - 1
    redis.call("LPOP", KEYS[2])
    oldest = redis.call("LINDEX", KEYS[2], 0)
    trimmed = true
end
if trimmed then
    if oldest then
        redis.call("XTRIM", KEYS[1], "MINID", string.match(oldest, "^([^:]+):"))
    else
        redis.call("XTRIM", KEYS[1], "MAXLEN", 0)
    end
    redis.call("HSET", KEYS[3], "bytes", bytes)
end
if bytes + #wire > max_bytes or count >= 65536 then
    return "FULL"
end
local id = redis.call("XADD", KEYS[1], "*", "wire", wire, "prev", previous)
redis.call("RPUSH", KEYS[2], id .. ":" .. #wire)
redis.call("HSET", KEYS[3], "last", id, "bytes", bytes + #wire)
local lifetime = math.max(retention + 60000, redis.call("PTTL", KEYS[5]))
for i = 1, 3 do
    redis.call("PEXPIRE", KEYS[i], lifetime)
end
return id
end

if #ARGV == 4 then return append(ARGV[1]) end
local accepted = 0
local result = append(ARGV[1])
if result == "FULL" then return accepted end
if type(result) == "table" then return result end
accepted = 1
for i = 5, #ARGV do
    result = append(ARGV[i])
    if result == "FULL" then break end
    if type(result) == "table" then return result end
    accepted = accepted + 1
end
return accepted
