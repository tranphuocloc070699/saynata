package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the saynata_admin cookie. If the token is good and the user is still an ADMIN,
 * the request counts as logged in. Otherwise nothing happens and Spring Security answers 401.
 * Not a @Component: SecurityConfig creates it, so it only runs inside the security chain.
 */
public class AdminCookieFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "saynata_admin";

    private final AdminTokenService tokens;
    private final UserRepository users;

    public AdminCookieFilter(AdminTokenService tokens, UserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = findCookie(request);
        if (token != null) {
            long userId = tokens.readUserId(token);
            // The role is checked in the database each time, so removing an admin works at once.
            User user = userId < 0 ? null : users.findById(userId).orElse(null);
            if (user != null && user.isAdmin()) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private String findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
