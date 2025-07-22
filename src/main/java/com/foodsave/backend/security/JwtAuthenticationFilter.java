package com.foodsave.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            String method = request.getMethod();
            
            // Пропускаем auth эндпоинты без авторизации
            if (path.equals("/api/auth/login") || 
                path.equals("/api/auth/register") || 
                path.equals("/api/auth/verify-email") ||
                path.equals("/api/auth/refresh-token") ||
                path.startsWith("/api/auth/dev-")) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // Пропускаем все публичные эндпоинты без авторизации
            if (path.startsWith("/api/stores") || 
                path.startsWith("/api/categories") || 
                path.startsWith("/api/products") ||
                path.startsWith("/api/permissions") ||
                path.startsWith("/stores") || 
                path.startsWith("/categories") || 
                path.startsWith("/products") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-resources") ||
                path.startsWith("/webjars") ||
                path.startsWith("/actuator")) {
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("Processing request: {} {}", method, path);

            String jwt = getJwtFromRequest(request);
            log.debug("JWT token present: {}", jwt != null);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                log.debug("JWT token valid for user: {}", username);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authentication set for user: {}", username);
            } else if (StringUtils.hasText(jwt)) {
                log.warn("Invalid JWT token for request: {} {}", method, path);
            }

        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
