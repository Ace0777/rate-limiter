local key_tokens      = KEYS[1] .. ":tokens"
local key_last_refill = KEYS[1] .. ":last_refill"

local capacity    = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now         = tonumber(ARGV[3])

local tokens      = tonumber(redis.call('GET', key_tokens))
local last_refill = tonumber(redis.call('GET', key_last_refill))

if tokens == nil then
    tokens      = capacity
    last_refill = now
end

local elapsed        = (now - last_refill) / 1000.0
local refilled       = elapsed * refill_rate
tokens               = math.min(capacity, tokens + refilled)


if tokens >= 1 then
    tokens = tokens - 1
    redis.call('SET', key_tokens,      tostring(tokens))
    redis.call('SET', key_last_refill, tostring(now))
    redis.call('EXPIRE', key_tokens,      3600)
    redis.call('EXPIRE', key_last_refill, 3600)
    return {1, math.floor(tokens), math.floor(capacity)}
else
    local wait_seconds = math.ceil((1 - tokens) / refill_rate)
    redis.call('SET', key_tokens,      tostring(tokens))
    redis.call('SET', key_last_refill, tostring(now))
    redis.call('EXPIRE', key_tokens,      3600)
    redis.call('EXPIRE', key_last_refill, 3600)
    return {0, 0, wait_seconds}
end