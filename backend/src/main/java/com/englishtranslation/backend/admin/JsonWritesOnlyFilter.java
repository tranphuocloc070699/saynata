package com.englishtranslation.backend.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF protection for /api/admin. The login cookie is SameSite=Strict, and on top of that every
 * write (POST, PUT, DELETE) must send "Content-Type: application/json". A web form on another site
 * cannot send that header, and a script on another site would need a CORS preflight that we refuse.
 * Not a @Component: SecurityConfig adds it to the security chain by hand.
 */
public class JsonWritesOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isAdminWrite = request.getRequestURI().startsWith("/api/admin/")
                && !READ_METHODS.contains(request.getMethod());
        if (isAdminWrite && !isJson(request.getContentType())) {
            response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Unsupported Media Type\",\"status\":415,"
                    + "\"detail\":\"Send Content-Type: application/json.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
    }
}
