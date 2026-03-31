local key       = KEYS[1]
local limit     = tonumber(ARGV[1])
local now       = tonumber(ARGV[2])
local window_ms = tonumber(ARGV[3])
local cutoff    = now - window_ms

redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)

local count = redis.call('ZCARD', key)

if count >= limit then
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retry_after = 0
    if oldest and oldest[2] then
        retry_after = math.ceil((tonumber(oldest[2]) + window_ms - now) / 1000)
    end
    return {0, count, retry_after}
end

local member = tostring(now) .. "-" .. redis.call('INCR', key .. ':seq')
redis.call('ZADD', key, now, member)
redis.call('EXPIRE', key, math.ceil(window_ms / 1000) + 1)

return {1, count + 1, 0}