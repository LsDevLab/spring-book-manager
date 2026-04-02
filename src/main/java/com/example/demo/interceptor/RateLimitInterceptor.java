package com.example.demo.interceptor;

import com.example.demo.exception.RateLimitExceededException;
import com.example.demo.security.AuthUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

// HandlerInterceptor — Spring MVC hook that runs BEFORE controller methods.
// Compare with JwtAuthenticationFilter (a Servlet Filter in the Security chain):
//   - Servlet Filters run earlier, before Spring MVC even dispatches the request.
//   - HandlerInterceptors run later, after security but before the controller.
// Rate limiting fits here because we need the authenticated user (set by the security filter).
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS = 5;
    private static final int WINDOW_SECONDS = 20;
    private static final String KEY_PREFIX = "rate_limit:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        int maxRequests = MAX_REQUESTS;
        int windowSeconds = WINDOW_SECONDS;

        if(handler instanceof HandlerMethod handlerMethod){
            RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
            if (rateLimit != null) {
                maxRequests = rateLimit.maxRequests();
                windowSeconds = rateLimit.windowSeconds();
            }
        }

        // 1. Identify the caller — use userId if authenticated, IP address otherwise.
        String identifier = resolveIdentifier(request);
        String key = KEY_PREFIX + identifier + ":" + request.getRequestURI();

        // 2. Atomic increment — Redis INCR command.
        //    If the key doesn't exist, Redis creates it with value 0 then increments → returns 1.
        //    If it exists, just increments → returns the new count.
        //    "Atomic" means even if 10 requests hit at the exact same time,
        //    each one gets a unique, correct count — no race conditions.
        Long requestCount = stringRedisTemplate.opsForValue().increment(key);

        // 3. First request in this window? Start the TTL countdown.
        //    We only set expire when count == 1 (key was just created).
        //    After WINDOW_SECONDS, Redis auto-deletes the key → counter resets to 0.
        if (requestCount != null && requestCount == 1) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        // 4. Over the limit? Block the request.
        if (requestCount != null && requestCount > maxRequests) {
            throw new RateLimitExceededException(
                    "Max " + maxRequests + " requests per " + windowSeconds + " seconds.");
        }

        // 5. Under the limit — let it through.
        return true;
    }

    // Extracts userId from the SecurityContext (set by JwtAuthenticationFilter).
    // Falls back to IP address for unauthenticated requests (e.g., /api/auth/login).
    private String resolveIdentifier(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof AuthUserDetails userDetails) {
            // Pattern matching with instanceof — Java 16+ feature.
            // Combines the type check and cast in one line:
            //   old way:  if (auth.getDetails() instanceof AuthUserDetails) { AuthUserDetails ud = (AuthUserDetails) auth.getDetails(); }
            //   new way:  if (auth.getDetails() instanceof AuthUserDetails ud) { /* ud is ready */ }
            return "user:" + userDetails.userId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
