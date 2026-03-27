
local current = redis.call('GET', KEYS[1])

if current and tonumber(current) >= tonumber(ARGV[1]) then
    local ttl = redis.call('TTL', KEYS[1])
    return {0, tonumber(current), ttl}
end


local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end

local ttl = redis.call('TTL', KEYS[1])
return {1, count, ttl}