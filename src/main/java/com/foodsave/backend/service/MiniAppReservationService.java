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
import com.foodsave.backend.exception.ResourceNotFoundException;
import com.foodsave.backend.repository.OrderRepository;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.security.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class MiniAppReservationService {

    private static final DateTimeFormatter RESERVATION_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMMM HH:mm", new Locale("ru"));

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final SecurityUtils securityUtils;
    private final TelegramBotService telegramBotService;

@Transactional
public OrderDTO createReservation(MiniAppReservationRequest request) {
        int quantity = request == null ? 1 : request.normalizedQuantity();
        Long productId = request != null ? request.productId() : null;

        if (productId == null) {
            throw new IllegalArgumentException("Product id is required for reservation");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (!productService.hasSufficientStock(product.getId(), quantity)) {
            log.warn("Insufficient stock for product {} (requested={}, available={})",
                    product.getId(), quantity, product.getStockQuantity());
            throw new IllegalArgumentException("Недостаточно коробок на складе. Попробуйте уменьшить количество или выбрать другой продукт.");
        }

        User user = securityUtils.getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Не удалось определить пользователя. Откройте мини-приложение через Telegram и повторите попытку.");
        }

        if (user.getTelegramUserId() == null) {
            log.warn("Mini-app reservation requested by user {} without linked telegram id", user.getId());
            throw new IllegalStateException("Не удалось найти ваш Telegram профиль. Откройте FoodSave Mini App из бота и попробуйте снова.");
        }

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

        try {
            productService.reduceStockQuantity(product.getId(), quantity);
        } catch (Exception ex) {
            log.error("Failed to reduce stock for product {} during reservation", product.getId(), ex);
            throw new IllegalStateException("Не удалось забронировать коробку: остатков недостаточно. Попробуйте выбрать другой товар.");
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Mini-app reservation order {} created for user {} (product {} store {})",
                savedOrder.getOrderNumber(), user.getId(), product.getId(),
                product.getStore() != null ? product.getStore().getId() : null);

        sendTelegramConfirmation(user, savedOrder, product);

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
        messageBuilder.append("Коробка: ").append(orUnknown(product.getName())).append("\n");
        messageBuilder.append("Магазин: ").append(store != null ? orUnknown(store.getName()) : "не указано").append("\n");
        if (store != null && store.getAddress() != null && !store.getAddress().isBlank()) {
            messageBuilder.append("Адрес: ").append(store.getAddress()).append("\n");
        }
        messageBuilder.append("Количество: ").append(quantity).append(" шт.\n");
        messageBuilder.append("Цена за шт.: ").append(formattedUnit).append("\n");
        messageBuilder.append("Сумма: ").append(formattedTotal).append("\n");
        messageBuilder.append("Статус: ожидает подтверждения\n");

        String formattedTime = RESERVATION_TIME_FORMAT.format(LocalDateTime.now());
        messageBuilder.append("Время бронирования: ").append(formattedTime);

        String reserverName = user.getFirstName() != null ? user.getFirstName() : "вас";
        messageBuilder.append("\n\nЗаказ закреплён за ").append(reserverName)
                .append(". Мы сообщим заведению и пришлём уведомление, когда коробка будет готова к выдаче. Если появятся вопросы — используйте команду /help.");

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
