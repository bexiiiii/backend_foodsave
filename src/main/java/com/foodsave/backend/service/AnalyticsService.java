package com.foodsave.backend.service;

import com.foodsave.backend.dto.AnalyticsDTO;
import com.foodsave.backend.entity.Order;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.entity.User;
import com.foodsave.backend.repository.OrderRepository;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.repository.StoreRepository;
import com.foodsave.backend.repository.UserRepository;
import com.foodsave.backend.domain.enums.OrderStatus;
import com.foodsave.backend.domain.enums.UserRole;
import com.foodsave.backend.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    public List<AnalyticsDTO.DailySalesAnalytics> getDailySalesAnalytics(LocalDate startDate, LocalDate endDate) {
        return getDailySalesAnalytics(startDate, endDate, null);
    }
    
    public List<AnalyticsDTO.DailySalesAnalytics> getDailySalesAnalytics(LocalDate startDate, LocalDate endDate, Long storeId) {
        log.info("Getting daily sales analytics for period {} to {}, storeId: {}", startDate, endDate, storeId);
        
        List<Store> stores = getAccessibleStores();
        
        if (stores.isEmpty()) {
            log.warn("No accessible stores found for current user");
            return List.of();
        }
        
        // Filter by specific store if provided
        if (storeId != null) {
            stores = stores.stream()
                    .filter(store -> store.getId().equals(storeId))
                    .collect(Collectors.toList());
            
            if (stores.isEmpty()) {
                log.warn("Store with ID {} not found or not accessible for current user", storeId);
                return List.of();
            }
        }
        
        log.info("Found {} accessible stores", stores.size());
        
        // Получаем ID магазинов для оптимизированного запроса
        List<Long> storeIds = stores.stream()
                .map(Store::getId)
                .collect(Collectors.toList());
        
        // Делаем один запрос для получения всех заказов за период
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        
        log.info("Fetching orders for stores {} from {} to {}", storeIds, startDateTime, endDateTime);
        
        List<Order> allOrders = orderRepository.findByStoreIdInAndCreatedAtBetween(
                storeIds, startDateTime, endDateTime);
        
        log.info("Found {} orders for the period", allOrders.size());
        
        // Группируем заказы по магазину и дате
        Map<String, List<Order>> ordersByStoreAndDate = allOrders.stream()
                .collect(Collectors.groupingBy(order -> {
                    LocalDate orderDate = order.getCreatedAt().toLocalDate();
                    return order.getStore().getId() + "_" + orderDate.toString();
                }));
        
        // Создаем результат для каждого дня и магазина
        List<AnalyticsDTO.DailySalesAnalytics> result = new ArrayList<>();
        
        for (Store store : stores) {
            startDate.datesUntil(endDate.plusDays(1)).forEach(date -> {
                String key = store.getId() + "_" + date.toString();
                List<Order> dayOrders = ordersByStoreAndDate.getOrDefault(key, List.of());
                
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
                
                result.add(AnalyticsDTO.DailySalesAnalytics.builder()
                        .storeId(store.getId())
                        .storeName(store.getName())
                        .date(date)
                        .totalOrders(dayOrders.size())
                        .completedOrders(completedOrders.size())
                        .canceledOrders(canceledOrders.size())
                        .totalRevenue(totalRevenue)
                        .completedRevenue(completedRevenue)
                        .canceledRevenue(canceledRevenue)
                        .build());
            });
        }
        
        log.info("Generated {} daily analytics records", result.size());
        return result;
    }

    public Map<String, Object> getGeneralAnalytics() {
        UserRole currentUserRole = securityUtil.getCurrentUserRole();
        
        Map<String, Object> data = new HashMap<>();
        
        if (currentUserRole == UserRole.SUPER_ADMIN) {
            // Супер админ видит все данные
            data.put("totalOrders", orderRepository.count());
            data.put("totalProducts", productRepository.count());
            data.put("totalStores", storeRepository.count());
            data.put("totalUsers", userRepository.count());
            
            // Calculate total revenue (handle null case)
            BigDecimal totalRevenue = orderRepository.sumTotal();
            data.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        } else {
            // Менеджеры и владельцы видят только данные своих магазинов
            List<Store> accessibleStores = getAccessibleStores();
            List<Long> storeIds = accessibleStores.stream()
                .map(Store::getId)
                .collect(Collectors.toList());
            
            if (!storeIds.isEmpty()) {
                long totalOrders = orderRepository.countByStoreIdIn(storeIds);
                long totalProducts = productRepository.countByStoreIdIn(storeIds);
                BigDecimal totalRevenue = orderRepository.sumTotalByStoreIdIn(storeIds);
                
                data.put("totalOrders", totalOrders);
                data.put("totalProducts", totalProducts);
                data.put("totalStores", accessibleStores.size());
                data.put("totalUsers", 0); // Менеджеры не видят общее количество пользователей
                data.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
                
                // Добавляем детальную статистику для сегодняшнего дня
                LocalDateTime today = LocalDate.now().atStartOfDay();
                LocalDateTime endOfToday = LocalDate.now().atTime(23, 59, 59);
                
                List<Order> todayOrders = orderRepository.findByStoreIdInAndCreatedAtBetween(storeIds, today, endOfToday);
                long todayCompletedOrders = todayOrders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                    .count();
                long todayCancelledOrders = todayOrders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.CANCELLED)
                    .count();
                
                BigDecimal todayRevenue = todayOrders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                data.put("todayOrders", todayOrders.size());
                data.put("todayCompletedOrders", todayCompletedOrders);
                data.put("todayCancelledOrders", todayCancelledOrders);
                data.put("todayRevenue", todayRevenue);
            } else {
                // Если у пользователя нет доступных магазинов
                data.put("totalOrders", 0);
                data.put("totalProducts", 0);
                data.put("totalStores", 0);
                data.put("totalUsers", 0);
                data.put("totalRevenue", BigDecimal.ZERO);
                data.put("todayOrders", 0);
                data.put("todayCompletedOrders", 0);
                data.put("todayCancelledOrders", 0);
                data.put("todayRevenue", BigDecimal.ZERO);
            }
        }
        
        return data;
    }
    
    /**
     * Получить магазины, доступные текущему пользователю
     */
    private List<Store> getAccessibleStores() {
        UserRole currentUserRole = securityUtil.getCurrentUserRole();
        Long currentUserId = securityUtil.getCurrentUserId();
        
        if (currentUserRole == UserRole.SUPER_ADMIN) {
            return storeRepository.findAll();
        } else if (currentUserRole == UserRole.STORE_MANAGER) {
            // Менеджер видит только магазин, которым управляет
            User manager = userRepository.findById(currentUserId).orElse(null);
            if (manager != null) {
                List<Store> managedStores = storeRepository.findByManager(manager, org.springframework.data.domain.Pageable.unpaged()).getContent();
                log.info("Manager {} has access to {} stores", currentUserId, managedStores.size());
                return managedStores;
            }
        } else if (currentUserRole == UserRole.STORE_OWNER) {
            // Владелец видит свои магазины
            User owner = userRepository.findById(currentUserId).orElse(null);
            if (owner != null) {
                List<Store> ownedStores = storeRepository.findByOwner(owner, org.springframework.data.domain.Pageable.unpaged()).getContent();
                log.info("Owner {} has access to {} stores", currentUserId, ownedStores.size());
                return ownedStores;
            }
        }
        
        return List.of(); // Пустой список для всех остальных ролей
    }
}
