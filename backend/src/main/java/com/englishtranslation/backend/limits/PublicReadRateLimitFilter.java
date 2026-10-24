package com.englishtranslation.backend.limits;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A generous per-IP limit on the public read endpoints (translations, words, suggest, translate-lines; default 120 per minute),
 * cache hits included.
 * It runs before the controller, so over the limit the answer is a real HTTP 429 even for the event stream.
 * Not a @Component: SecurityConfig adds it to the security chain by hand.
 */
public class PublicReadRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final LimitProperties limits;

    public PublicReadRateLimitFilter(RateLimiter rateLimiter, LimitProperties limits) {
        this.rateLimiter = rateLimiter;
        this.limits = limits;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/translations") || path.startsWith("/api/words/")
                || path.equals("/api/suggest") || path.equals("/api/translate-lines"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter.Decision decision = rateLimiter.tryAcquire(ClientIp.of(request), limits.publicReadPerIpLimit());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many requests. Try again in a little while.\",\"code\":\"rate_limited\"}");
    }
}
