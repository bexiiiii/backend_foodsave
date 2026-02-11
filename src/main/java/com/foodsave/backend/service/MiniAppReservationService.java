package com.foodsave.backend.service;

import com.foodsave.backend.domain.enums.OrderStatus;
import com.foodsave.backend.domain.enums.PaymentMethod;
import com.foodsave.backend.domain.enums.PaymentStatus;
import com.foodsave.backend.dto.OrderDTO;
import com.foodsave.backend.dto.miniapp.MiniAppReservationRequest;
import com.foodsave.backend.entity.Order;
import com.foodsave.backend.entity.OrderItem;
import com.foodsave.backend.entity.Product;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.entity.User;
import com.foodsave.backend.exception.InsufficientStockException;
import com.foodsave.backend.exception.ResourceNotFoundException;
import com.foodsave.backend.repository.OrderRepository;
import com.foodsave.backend.security.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MiniAppReservationService {

    private static final DateTimeFormatter RESERVATION_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMMM HH:mm", new Locale("ru"));
    private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Asia/Almaty");

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final SecurityUtils securityUtils;
    private final TelegramBotService telegramBotService;

    @Transactional
    public OrderDTO createReservation(MiniAppReservationRequest request) {
        log.info("=== RESERVATION START === request={}", request);
        
        int quantity = request == null ? 1 : Math.max(1, request.normalizedQuantity());
        Long productId = request != null ? request.productId() : null;

        log.info("Parsed request: productId={}, quantity={}", productId, quantity);

        if (productId == null) {
            log.error("Product ID is null");
            throw new IllegalArgumentException("Product id is required for reservation");
        }

        User user = securityUtils.getCurrentUser();
        if (user == null) {
            log.error("getCurrentUser() returned null");
            throw new IllegalStateException("Не удалось определить пользователя. Откройте мини-приложение через Telegram и повторите попытку.");
        }

        log.info("User authenticated: userId={}, telegramId={}", 
            user.getId(), user.getTelegramUserId());

        if (user.getTelegramUserId() == null) {
            log.warn("Mini-app reservation requested by user {} without linked telegram id", user.getId());
            throw new IllegalStateException("Не удалось найти ваш Telegram профиль. Откройте FoodSave Mini App из бота и попробуйте снова.");
        }

        Product product;
        try {
            log.info("Reserving product stock...");
            product = productService.reserveProductStock(productId, quantity);
            log.info("Product reserved: id={}, name={}, price={}", 
                product.getId(), product.getName(), product.getPrice());
        } catch (EntityNotFoundException ex) {
            log.error("Product not found: {}", productId);
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        } catch (InsufficientStockException ex) {
            log.warn("Insufficient stock for product {} (requested={})", productId, quantity);
            throw new IllegalArgumentException("Недостаточно коробок на складе. Попробуйте уменьшить количество или выбрать другой продукт.");
        }

        log.info("Creating order...");
        Order order = new Order();
        order.setUser(user);
        order.setStore(product.getStore());
        order.setOrderNumber(generateUniqueOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setContactPhone(resolvePhone(user));
        order.setDeliveryAddress(product.getStore() != null ? product.getStore().getAddress() : null);
        order.setDeliveryNotes(request.note() != null && !request.note().isBlank()
                ? request.note().trim()
                : "Telegram бронирование через мини-приложение");

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);

        BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.calculateTotalPrice();

        order.addItem(item);
        order.calculateTotals();

        log.info("Saving order: orderNumber={}", order.getOrderNumber());
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved: orderId={}, orderNumber={}", 
            savedOrder.getId(), savedOrder.getOrderNumber());
        
        log.info("Mini-app reservation order {} created for user {} (product {} store {})",
                savedOrder.getOrderNumber(), user.getId(), product.getId(),
                product.getStore() != null ? product.getStore().getId() : null);

        log.info("Sending Telegram confirmation...");
        try {
            sendTelegramConfirmation(user, savedOrder, product);
            log.info("Telegram confirmation sent");
        } catch (Exception e) {
            log.error("Failed to send Telegram confirmation", e);
        }

        log.info("=== RESERVATION COMPLETE === orderId={}", savedOrder.getId());
        return OrderDTO.fromEntity(savedOrder);
    }

    private void sendTelegramConfirmation(User user, Order order, Product product) {
        Long chatId = user.getTelegramUserId();
        if (chatId == null) {
            log.warn("Skipping Telegram confirmation for order {} because chat id is missing", order.getId());
            return;
        }

        String message = buildConfirmationMessage(user, order, product);
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                message,
                null,
                null,
                null
        ));
    }

    private String buildConfirmationMessage(User user, Order order, Product product) {
        Store store = product.getStore();
        OrderItem item = order.getItems().isEmpty() ? null : order.getItems().get(0);
        int quantity = item != null && item.getQuantity() != null ? item.getQuantity() : 1;

        String formattedTotal = formatPrice(item != null ? item.getTotalPrice() : order.getTotal());
        String formattedUnit = formatPrice(item != null ? item.getUnitPrice() : product.getPrice());

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("🧾 Заказ №").append(order.getOrderNumber()).append("\n");
        messageBuilder.append("Бокс: ").append(orUnknown(product.getName())).append("\n");
        messageBuilder.append("Магазин: ").append(store != null ? orUnknown(store.getName()) : "не указано").append("\n");
        if (store != null && store.getAddress() != null && !store.getAddress().isBlank()) {
            messageBuilder.append("Адрес: ").append(store.getAddress()).append("\n");
        }
        messageBuilder.append("Количество: ").append(quantity).append(" шт.\n");
        messageBuilder.append("Цена за шт.: ").append(formattedUnit).append("\n");
        messageBuilder.append("Сумма: ").append(formattedTotal).append("\n");
       

        String formattedTime = RESERVATION_TIME_FORMAT.format(LocalDateTime.now(DEFAULT_TIME_ZONE));
        messageBuilder.append("Время бронирования: ").append(formattedTime);

        String reserverName = user.getFirstName() != null ? user.getFirstName() : "вас";
        messageBuilder.append("\n\nЗаказ закреплён за ").append(reserverName)
                .append(".  Если появятся вопросы — используйте команду /help.");

        return messageBuilder.toString();
    }

    private String formatPrice(BigDecimal value) {
        BigDecimal target = value != null ? value : BigDecimal.ZERO;
        String formatted = String.format(Locale.US, "%,.0f ₸", target.doubleValue());
        return formatted.replace(',', ' ');
    }

    private String orUnknown(String value) {
        if (value == null || value.isBlank()) {
            return "не указано";
        }
        return value;
    }

    private String resolvePhone(User user) {
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return user.getPhone();
        }
        return "+7 000 000 0000";
    }

    private String generateUniqueOrderNumber() {
        String orderNumber;
        do {
            orderNumber = generateOrderNumber();
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    private String generateOrderNumber() {
        int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return String.format("%06d", random);
    }
}
