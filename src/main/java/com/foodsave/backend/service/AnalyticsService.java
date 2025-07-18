package com.foodsave.backend.service;

import com.foodsave.backend.dto.AnalyticsDTO;
import com.foodsave.backend.entity.Order;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.repository.OrderRepository;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.repository.StoreRepository;
import com.foodsave.backend.repository.UserRepository;
import com.foodsave.backend.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    public List<AnalyticsDTO.DailySalesAnalytics> getDailySalesAnalytics(LocalDate startDate, LocalDate endDate) {
        List<Store> stores = storeRepository.findAll();
        
        return stores.stream()
                .flatMap(store -> {
                    return startDate.datesUntil(endDate.plusDays(1))
                            .map(date -> {
                                LocalDateTime startOfDay = date.atStartOfDay();
                                LocalDateTime endOfDay = date.atTime(23, 59, 59);
                                
                                List<Order> dayOrders = orderRepository.findByStoreAndCreatedAtBetween(
                                        store, startOfDay, endOfDay);
                                
                                List<Order> completedOrders = dayOrders.stream()
                                        .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                                        .collect(Collectors.toList());
                                
                                List<Order> canceledOrders = dayOrders.stream()
                                        .filter(order -> order.getStatus() == OrderStatus.CANCELLED)
                                        .collect(Collectors.toList());
                                
                                BigDecimal totalRevenue = dayOrders.stream()
                                        .map(Order::getTotal)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                
                                BigDecimal completedRevenue = completedOrders.stream()
                                        .map(Order::getTotal)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                
                                BigDecimal canceledRevenue = canceledOrders.stream()
                                        .map(Order::getTotal)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                
                                return AnalyticsDTO.DailySalesAnalytics.builder()
                                        .storeId(store.getId())
                                        .storeName(store.getName())
                                        .date(date)
                                        .totalOrders(dayOrders.size())
                                        .completedOrders(completedOrders.size())
                                        .canceledOrders(canceledOrders.size())
                                        .totalRevenue(totalRevenue)
                                        .completedRevenue(completedRevenue)
                                        .canceledRevenue(canceledRevenue)
                                        .build();
                            });
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getGeneralAnalytics() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", orderRepository.count());
        data.put("totalProducts", productRepository.count());
        data.put("totalStores", storeRepository.count());
        data.put("totalUsers", userRepository.count());
        
        // Calculate total revenue (handle null case)
        BigDecimal totalRevenue = orderRepository.sumTotal();
        data.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        
        return data;
    }
}
