package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.limits.CallerContext;
import com.englishtranslation.backend.limits.ClientIp;
import com.englishtranslation.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads "Authorization: Bearer <access token>". The token is optional: with no token, or a bad or expired one,
 * the request simply stays a guest request (no error here). With a good token whose user still exists, the
 * request counts as that user (role USER) and {@link CallerContext} knows the user id for the Claude gate.
 * It never looks at /api/admin, which has its own cookie login. Not a @Component: SecurityConfig adds it.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final AccessTokenService tokens;
    private final UserRepository users;

    public BearerTokenFilter(AccessTokenService tokens, UserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = readUserId(request);
        if (userId != null) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        CallerContext.Caller before = CallerContext.set(userId, ClientIp.of(request));
        try {
            chain.doFilter(request, response);
        } finally {
            CallerContext.restore(before);
        }
    }

    /** The user id of a good token whose user still exists (a deleted account stops working at once), else null. */
    private Long readUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        long userId = tokens.readUserId(header.substring(PREFIX.length()).trim());
        return userId >= 0 && users.existsById(userId) ? userId : null;
    }
}
