package com.ratelimiter.api_gateway.interfaces.filter;

import com.ratelimiter.api_gateway.application.usecase.CheckRateLimitUseCase;
import com.ratelimiter.api_gateway.domain.model.RateLimitResult;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(1)                          // primeiro filtro a rodar
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private final CheckRateLimitUseCase checkRateLimit;

    @Override
    public void doFilter(ServletRequest req,
                         ServletResponse res,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String apiKey   = request.getHeader("X-API-Key");
        String endpoint = request.getRequestURI();


        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing X-API-Key header\"}");
            return;
        }

        RateLimitResult result = checkRateLimit.execute(apiKey, endpoint);


        addRateLimitHeaders(response, result);

        if (!result.isAllowed()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    String.format("{\"error\": \"Rate limit exceeded\", \"retryAfter\": %d}",
                            result.getRetryAfterSeconds())
            );
            return;
        }

        chain.doFilter(req, res);
    }

    private void addRateLimitHeaders(HttpServletResponse response,
                                     RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit",     String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(result.getResetEpochSeconds()));

        if (!result.isAllowed()) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        }
    }
}
