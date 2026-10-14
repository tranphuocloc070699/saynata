package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.user.UserRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Who may call what.
 * - POST /api/admin/auth/login and /logout are open (login must work without being logged in).
 * - Everything else under /api/admin needs the ADMIN role.
 * - All other URLs (/api/translations, /api/words, /actuator/health) stay open, as before.
 * No session is kept on the server: each request brings its own cookie.
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<AdminTokenService> tokens,
            UserRepository users)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // replaced by JsonWritesOnlyFilter + the SameSite=Strict cookie
                .cors(Customizer.withDefaults()) // uses the corsConfigurationSource bean below
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS, "/api/admin/**").permitAll() // CORS preflight
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/logout").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll());
        // When the admin API is switched off there is no cookie filter, so every /api/admin call gets 401.
        tokens.ifAvailable(service -> http.addFilterBefore(
                new AdminCookieFilter(service, users), UsernamePasswordAuthenticationFilter.class));
        http.addFilterBefore(new JsonWritesOnlyFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** CORS only for /api/admin: the admin web app may call it with its cookie. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AdminProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        cors.setExposedHeaders(List.of("X-Total-Count"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", cors);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
