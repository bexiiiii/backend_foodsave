package com.foodsave.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDTO {
    private Long storeId;
    private String storeName;
    private LocalDate date;
    private Integer totalOrders;
    private Integer completedOrders;
    private Integer canceledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal completedRevenue;
    private BigDecimal canceledRevenue;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySalesAnalytics {
        private Long storeId;
        private String storeName;
        private LocalDate date;
        private Integer totalOrders;
        private Integer completedOrders;
        private Integer canceledOrders;
        private BigDecimal totalRevenue;
        private BigDecimal completedRevenue;
        private BigDecimal canceledRevenue;
    }
} 