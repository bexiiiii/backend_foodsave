package com.foodsave.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter для защиты от DDoS и сканирования
 * Ограничивает количество запросов с одного IP адреса
 */
@Component
@Slf4j
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    // Максимум 100 запросов в минуту с одного IP
    private static final int REQUESTS_PER_MINUTE = 100;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIp = getClientIP(httpRequest);
        
        // Получаем или создаём bucket для данного IP
        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createNewBucket());
        
        if (bucket.tryConsume(1)) {
            // Запрос разрешён
            chain.doFilter(request, response);
        } else {
            // Превышен лимит запросов
            log.warn("⚠️ Rate limit exceeded for IP: {} on path: {}", 
                    clientIp, httpRequest.getRequestURI());
            
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"Too many requests. Please try again later.\",\"code\":429}"
            );
        }
    }

    private Bucket createNewBucket() {
        // Создаём bucket с лимитом REQUESTS_PER_MINUTE запросов в минуту
        Bandwidth limit = Bandwidth.classic(
            REQUESTS_PER_MINUTE,
            Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("🛡️ Rate Limiting Filter initialized - {} requests per minute per IP", 
                REQUESTS_PER_MINUTE);
    }

    public void destroy() {
        buckets.clear();
    }
}
