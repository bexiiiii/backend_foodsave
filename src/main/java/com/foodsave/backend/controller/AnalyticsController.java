package com.foodsave.backend.controller;

import com.foodsave.backend.dto.AnalyticsDTO;
import com.foodsave.backend.service.AnalyticsService;
import com.foodsave.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/daily-sales")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STORE_MANAGER') or hasRole('STORE_OWNER')")
    public ResponseEntity<List<AnalyticsDTO.DailySalesAnalytics>> getDailySalesAnalytics(
            @RequestParam(defaultValue = "2024-01-01") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(defaultValue = "2024-12-31") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long storeId) {
        try {
            List<AnalyticsDTO.DailySalesAnalytics> analytics = analyticsService.getDailySalesAnalytics(startDate, endDate, storeId);
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            throw new ApiException("Failed to fetch daily sales analytics: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/general")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STORE_MANAGER') or hasRole('STORE_OWNER')")
    public ResponseEntity<Map<String, Object>> getGeneralAnalytics() {
        try {
            return ResponseEntity.ok(analyticsService.getGeneralAnalytics());
        } catch (Exception e) {
            throw new ApiException("Failed to fetch general analytics: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STORE_MANAGER') or hasRole('STORE_OWNER')")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        try {
            Map<String, Object> analytics = analyticsService.getGeneralAnalytics();
            // Создаем обертку для соответствия формату фронтенда
            return ResponseEntity.ok(Map.of("data", analytics));
        } catch (Exception e) {
            throw new ApiException("Failed to fetch analytics: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
