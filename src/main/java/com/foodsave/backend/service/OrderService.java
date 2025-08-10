package com.foodsave.backend.service;

import com.foodsave.backend.domain.enums.OrderStatus;
import com.foodsave.backend.domain.enums.PaymentMethod;
import com.foodsave.backend.domain.enums.PaymentStatus;
import com.foodsave.backend.dto.OrderDTO;
import com.foodsave.backend.dto.OrderItemDTO;
import com.foodsave.backend.dto.OrderStatsDTO;
import com.foodsave.backend.dto.StoreOrderStatsDTO;
import com.foodsave.backend.entity.Order;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.entity.User;
import com.foodsave.backend.entity.OrderItem;
import com.foodsave.backend.entity.Product;
import com.foodsave.backend.repository.OrderRepository;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.exception.ResourceNotFoundException;
import com.foodsave.backend.security.SecurityUtils;
import com.foodsave.backend.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final SecurityUtils securityUtils;
    private final SecurityUtil securityUtil;
    private final com.foodsave.backend.repository.UserRepository userRepository;
    private final com.foodsave.backend.repository.StoreRepository storeRepository;

    private static final String ORDER_NUMBER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ORDER_NUMBER_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    public List<OrderDTO> getAllOrders() {
        try {
            log.info("DEBUG: Getting all orders");
            if (securityUtil.isCurrentUserAdmin()) {
                // Super admins see all orders
                log.info("DEBUG: User is admin, fetching all orders");
                List<Order> orders = orderRepository.findAll();
                log.info("DEBUG: Found {} orders in database", orders.size());
                return orders.stream()
                        .map(OrderDTO::fromEntity)
                        .collect(Collectors.toList());
            } else {
                // Check if user is a store manager
                org.springframework.security.core.Authentication authentication = 
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                
                if (authentication != null && authentication.getPrincipal() instanceof com.foodsave.backend.security.UserPrincipal) {
                    com.foodsave.backend.security.UserPrincipal userPrincipal = 
                        (com.foodsave.backend.security.UserPrincipal) authentication.getPrincipal();
                    
                    if (userPrincipal.getRole() == com.foodsave.backend.domain.enums.UserRole.STORE_MANAGER) {
                        // Manager sees only orders from their managed store
                        log.info("DEBUG: User is store manager, fetching managed store orders");
                        Long managedStoreId = getCurrentManagedStoreId(userPrincipal.getId());
                        if (managedStoreId != null) {
                            List<Order> orders = orderRepository.findByStoreId(managedStoreId, org.springframework.data.domain.Pageable.unpaged()).getContent();
                            log.info("DEBUG: Found {} orders for managed store {}", orders.size(), managedStoreId);
                            return orders.stream()
                                    .map(OrderDTO::fromEntity)
                                    .collect(Collectors.toList());
                        }
                    }
                }
                
                // Store owners see only their store's orders
                log.info("DEBUG: User is store owner, fetching store orders");
                Set<Long> userStoreIds = securityUtil.getCurrentUserStoreIds();
                log.info("DEBUG: User store IDs: {}", userStoreIds);
                if (userStoreIds.isEmpty()) {
                    return List.of();
                }
                return orderRepository.findByStoreIdIn(userStoreIds).stream()
                        .map(OrderDTO::fromEntity)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Error fetching all orders: ", e);
            throw new RuntimeException("Failed to fetch orders: " + e.getMessage());
        }
    }

    private Long getCurrentManagedStoreId(Long managerId) {
        try {
            com.foodsave.backend.entity.User manager = userRepository.findById(managerId).orElse(null);
            
            if (manager != null) {
                // Поиск заведения, которым управляет данный менеджер
                org.springframework.data.domain.Page<com.foodsave.backend.entity.Store> stores = 
                    storeRepository.findByManager(manager, org.springframework.data.domain.Pageable.unpaged());
                
                if (!stores.getContent().isEmpty()) {
                    return stores.getContent().get(0).getId();
                }
            }
        } catch (Exception e) {
            log.error("Error finding managed store: ", e);
        }
        return null;
    }

    public OrderDTO getOrderById(Long id) {
        return orderRepository.findById(id)
                .map(OrderDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    public List<OrderDTO> getCurrentUserOrders() {
        User currentUser = securityUtils.getCurrentUser();
        return orderRepository.findByUser(currentUser).stream()
                .map(OrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<OrderDTO> getCurrentStoreOrders() {
        Store currentStore = securityUtils.getCurrentStore();
        return orderRepository.findByStore(currentStore).stream()
                .map(OrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private BigDecimal calculateOrderTotal(List<OrderItem> items) {
        return items.stream()
            .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Generates a unique random 6-character alphanumeric order number
     */
    private String generateUniqueOrderNumber() {
        String orderNumber;
        do {
            StringBuilder sb = new StringBuilder(ORDER_NUMBER_LENGTH);
            for (int i = 0; i < ORDER_NUMBER_LENGTH; i++) {
                int index = random.nextInt(ORDER_NUMBER_CHARS.length());
                sb.append(ORDER_NUMBER_CHARS.charAt(index));
            }
            orderNumber = sb.toString();
        } while (orderRepository.existsByOrderNumber(orderNumber)); // Ensure uniqueness
        
        return orderNumber;
    }

    @Transactional
    public OrderDTO createOrder(OrderDTO orderDTO) {
        log.info("Creating new order: {}", orderDTO);
        
        // Validate store exists
        Store store = productRepository.findById(orderDTO.getItems().get(0).getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + orderDTO.getItems().get(0).getProductId()))
            .getStore();
        
        // Create order
        Order order = new Order();
        order.setStore(store);
        order.setUser(securityUtils.getCurrentUser());
        order.setOrderNumber(generateUniqueOrderNumber()); // Generate unique order number
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(orderDTO.getPaymentMethod());
        order.setContactPhone(orderDTO.getContactPhone());
        order.setDeliveryAddress(orderDTO.getDeliveryAddress());
        order.setDeliveryNotes(orderDTO.getDeliveryNotes());
        
        // Process order items
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemDTO itemDTO : orderDTO.getItems()) {
            Product product = productRepository.findById(itemDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemDTO.getProductId()));
            
            // Check if sufficient stock is available using ProductService
            if (!productService.hasSufficientStock(itemDTO.getProductId(), itemDTO.getQuantity())) {
                throw new IllegalArgumentException("Insufficient stock for product '" + product.getName() + 
                    "'. Available: " + product.getStockQuantity() + ", Requested: " + itemDTO.getQuantity());
            }
            
            // Reduce stock quantity using ProductService
            productService.reduceStockQuantity(itemDTO.getProductId(), itemDTO.getQuantity());
            
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.calculateTotalPrice();
            
            orderItems.add(orderItem);
        }
        
        // Set order items and calculate total
        order.setItems(orderItems);
        order.setTotal(calculateOrderTotal(orderItems));
        
        // Save order
        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully: {}", savedOrder);
        
        return OrderDTO.fromEntity(savedOrder);
    }

    @Transactional
    public OrderDTO updateOrder(Long id, OrderDTO orderDTO) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        
        // Update basic order properties
        if (orderDTO.getContactPhone() != null) {
            order.setContactPhone(orderDTO.getContactPhone());
        }
        
        if (orderDTO.getPaymentMethod() != null) {
            order.setPaymentMethod(orderDTO.getPaymentMethod());
        }
        
        if (orderDTO.getStatus() != null) {
            order.setStatus(orderDTO.getStatus());
        }
        
        // Update order items if provided
        if (orderDTO.getItems() != null && !orderDTO.getItems().isEmpty()) {
            // Clear existing items
            order.getItems().clear();
            order.setSubtotal(BigDecimal.ZERO);
            
            // Validate all products belong to the same store as the order
            for (OrderItemDTO itemDTO : orderDTO.getItems()) {
                Product product = productRepository.findById(itemDTO.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemDTO.getProductId()));
                
                if (!product.getStore().getId().equals(order.getStore().getId())) {
                    throw new IllegalArgumentException("All products must belong to the same store as the original order");
                }
                
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setProduct(product);
                orderItem.setQuantity(itemDTO.getQuantity());
                orderItem.setUnitPrice(product.getPrice());
                orderItem.calculateTotalPrice();
                
                order.getItems().add(orderItem);
            }
            
            // Recalculate totals
            order.calculateTotals();
        }
        
        return OrderDTO.fromEntity(orderRepository.save(order));
    }

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }

    public OrderDTO updateOrderStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        order.setStatus(status);
        return OrderDTO.fromEntity(orderRepository.save(order));
    }

    // Статистика заказов для всех заведений (только для админов)
    public OrderStatsDTO getOrdersStats() {
        List<Order> orders;
        
        if (securityUtil.isCurrentUserAdmin()) {
            // Админы видят статистику по всем заказам
            orders = orderRepository.findAll();
        } else {
            // Владельцы заведений и менеджеры видят только свои заказы
            Set<Long> userStoreIds = securityUtil.getCurrentUserStoreIds();
            if (userStoreIds.isEmpty()) {
                return new OrderStatsDTO(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
            }
            orders = orderRepository.findByStoreIdIn(userStoreIds);
        }
        
        return calculateOrderStats(orders);
    }

    // Статистика заказов по заведениям (только для админов)
    public List<StoreOrderStatsDTO> getOrdersStatsByStore() {
        List<Order> orders;
        
        if (securityUtil.isCurrentUserAdmin()) {
            // Админы видят статистику по всем заведениям
            orders = orderRepository.findAll();
            log.info("Admin user - found {} total orders", orders.size());
        } else {
            // Владельцы заведений и менеджеры видят только свои заведения
            Set<Long> userStoreIds = securityUtil.getCurrentUserStoreIds();
            if (userStoreIds.isEmpty()) {
                log.warn("No store IDs found for current user");
                return List.of();
            }
            log.info("Store user - found store IDs: {}", userStoreIds);
            orders = orderRepository.findByStoreIdIn(userStoreIds);
            log.info("Found {} orders for user's stores", orders.size());
        }
        
        Map<Store, List<Order>> groupedOrders = orders.stream()
                .collect(Collectors.groupingBy(Order::getStore));
        
        log.info("Grouped orders by {} stores", groupedOrders.size());
        
        return groupedOrders.entrySet().stream()
                .map(entry -> {
                    Store store = entry.getKey();
                    List<Order> storeOrders = entry.getValue();
                    OrderStatsDTO stats = calculateOrderStats(storeOrders);
                    
                    log.info("Store '{}' has {} orders", store.getName(), storeOrders.size());
                    
                    return new StoreOrderStatsDTO(
                            store.getId(),
                            store.getName(),
                            store.getLogo(),
                            stats.getTotalOrders(),
                            stats.getSuccessfulOrders(),
                            stats.getFailedOrders(),
                            stats.getPendingOrders(),
                            stats.getConfirmedOrders(),
                            stats.getPreparingOrders(),
                            stats.getReadyOrders(),
                            stats.getPickedUpOrders(),
                            stats.getDeliveredOrders(),
                            stats.getCancelledOrders()
                    );
                })
                .collect(Collectors.toList());
    }

    // Статистика заказов для текущего заведения (для менеджеров заведений)
    public OrderStatsDTO getMyStoreOrdersStats() {
        Store currentStore = securityUtil.getCurrentStore();
        List<Order> orders = orderRepository.findByStore(currentStore);
        return calculateOrderStats(orders);
    }

    private OrderStatsDTO calculateOrderStats(List<Order> orders) {
        long totalOrders = orders.size();
        
        long pendingOrders = orders.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.PENDING ? 1 : 0)
                .sum();
        
        long confirmedOrders = orders.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.CONFIRMED ? 1 : 0)
                .sum();
        
        long preparingOrders = orders.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.PREPARING ? 1 : 0)
                .sum();
        
        long readyOrders = orders.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.READY_FOR_PICKUP ? 1 : 0)
                .sum();
        
        long outForDeliveryOrders = orders.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.OUT_FOR_DELIVERY ? 1 : 0)
                .sum();
        
        long deliveredOrders = orders.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.DELIVERED ? 1 : 0)
                .sum();
        
        long cancelledOrders = orders.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.CANCELLED ? 1 : 0)
                .sum();
        
        // Успешные заказы - это те, что доставлены
        long successfulOrders = deliveredOrders;
        
        // Неуспешные заказы - это отмененные
        long failedOrders = cancelledOrders;
        
        return new OrderStatsDTO(
                totalOrders,
                successfulOrders,
                failedOrders,
                pendingOrders,
                confirmedOrders,
                preparingOrders,
                readyOrders,
                0L, // pickedUpOrders - не используется в текущем enum
                deliveredOrders,
                cancelledOrders
        );
    }
}
