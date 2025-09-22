package com.foodsave.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodsave.backend.dto.telegram.TelegramCallbackQuery;
import com.foodsave.backend.dto.telegram.TelegramMessage;
import com.foodsave.backend.dto.telegram.TelegramUpdate;
import com.foodsave.backend.dto.telegram.TelegramUser;
import com.foodsave.backend.dto.telegram.TelegramWebAppData;
import com.foodsave.backend.entity.Order;
import com.foodsave.backend.entity.OrderItem;
import com.foodsave.backend.entity.Product;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.entity.User;
import com.foodsave.backend.domain.enums.OrderStatus;
import com.foodsave.backend.domain.enums.PaymentMethod;
import com.foodsave.backend.domain.enums.PaymentStatus;
import com.foodsave.backend.repository.OrderRepository;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookService {

    private final TelegramBotService telegramBotService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;

    @Value("${telegram.miniapp.base-url:https://miniapp.foodsave.kz}")
    private String miniAppBaseUrl;

    @Value("${telegram.support.username:@FoodSave_kz}")
    private String supportUsername;

    private static final DateTimeFormatter RESERVATION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("ru"));

    @Transactional
    public void handleUpdate(TelegramUpdate update) {
        if (update == null) {
            return;
        }

        TelegramMessage message = resolveMessage(update);
        TelegramUser from = resolveUser(update);
        Long chatId = resolveChatId(message);

        if (chatId == null) {
            log.warn("Received Telegram update without chat id: {}", update);
            return;
        }

        if (handleWebAppData(update, message, from, chatId)) {
            return;
        }

        if (message != null && message.text() != null) {
            handleTextMessage(message.text(), chatId, from);
        }
    }

    private TelegramMessage resolveMessage(TelegramUpdate update) {
        if (update.message() != null) {
            return update.message();
        }
        TelegramCallbackQuery callbackQuery = update.callbackQuery();
        if (callbackQuery != null) {
            return callbackQuery.message();
        }
        return null;
    }

    private TelegramUser resolveUser(TelegramUpdate update) {
        if (update.message() != null && update.message().from() != null) {
            return update.message().from();
        }
        TelegramCallbackQuery callbackQuery = update.callbackQuery();
        if (callbackQuery != null) {
            if (callbackQuery.webAppData() != null && callbackQuery.from() != null) {
                return callbackQuery.from();
            }
            TelegramMessage message = callbackQuery.message();
            if (message != null) {
                return message.from();
            }
        }
        return null;
    }

    private Long resolveChatId(TelegramMessage message) {
        if (message != null && message.chat() != null) {
            return message.chat().id();
        }
        return null;
    }

    private boolean handleWebAppData(TelegramUpdate update,
                                      TelegramMessage message,
                                      TelegramUser from,
                                      Long chatId) {
        TelegramWebAppData webAppData = null;
        if (message != null && message.webAppData() != null) {
            webAppData = message.webAppData();
        } else if (update.callbackQuery() != null) {
            webAppData = update.callbackQuery().webAppData();
        }

        if (webAppData == null || webAppData.data() == null || webAppData.data().isBlank()) {
            return false;
        }

        try {
            ReservationPayload payload = objectMapper.readValue(webAppData.data(), ReservationPayload.class);
            respondToReservation(chatId, from, payload);
        } catch (Exception e) {
            log.error("Failed to parse web_app_data: {}", webAppData.data(), e);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Не удалось обработать бронирование. Пожалуйста, попробуйте ещё раз.",
                    null,
                    null,
                    null
            ));
        }
        return true;
    }

    private void handleTextMessage(String text, Long chatId, TelegramUser from) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String command = trimmed.split("\\s+")[0];

        if (command.equalsIgnoreCase("/start") || command.toLowerCase(Locale.ROOT).startsWith("/start@")) {
            sendWelcomeMessage(chatId);
            return;
        }

        if (command.equalsIgnoreCase("/help") || command.toLowerCase(Locale.ROOT).startsWith("/help@")) {
            sendSupportMessage(chatId);
            return;
        }

        // Fallback for any other text input
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "Чтобы выбрать коробку со скидкой, откройте мини‑приложение FoodSave и нажмите «Забронировать» на нужном товаре.",
                null,
                null,
                null
        ));
    }

    private void sendWelcomeMessage(Long chatId) {
        String welcomeText = String.join("\n",
                "Привет! 👋",
                "Я бот FoodSave.",
                "Открой мини‑приложение, чтобы выбрать коробку со скидкой и забронировать её.");

        String webAppUrl = ensureHttps(miniAppBaseUrl);
        telegramBotService.sendWebAppMessage(chatId, welcomeText, "Открыть FoodSave Mini App", webAppUrl);
    }

    private void sendSupportMessage(Long chatId) {
        String supportText = String.join("\n",
                "Нужна помощь? Мы всегда на связи!",
                "Напишите в поддержку: " + supportUsername,
                "Или откройте мини‑приложение, чтобы оформить заказ заново.");

        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                supportText,
                null,
                null,
                null
        ));
    }

    private void respondToReservation(Long chatId, TelegramUser from, ReservationPayload payload) {
        ReservationResult reservationResult = createReservationOrder(from, payload);
        if (!reservationResult.success()) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    reservationResult.errorMessage(),
                    null,
                    null,
                    null
            ));
            return;
        }

        Order order = reservationResult.order();
        String reserverName = from != null ? from.displayName() : "вас";
        String formattedTotal = formatPrice(order.getTotal().doubleValue());
        String formattedUnit = formatPrice(order.getItems().get(0).getUnitPrice().doubleValue());
        String formattedTime = formatTimestamp(payload.timestamp());

        Product product = reservationResult.product();
        Store store = product.getStore();

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("🧾 Заказ №").append(order.getOrderNumber()).append("\n");
        messageBuilder.append("Коробка: ").append(orUnknown(product.getName())).append("\n");
        messageBuilder.append("Магазин: ").append(orUnknown(store.getName())).append("\n");
        if (store.getAddress() != null && !store.getAddress().isBlank()) {
            messageBuilder.append("Адрес: ").append(store.getAddress()).append("\n");
        }
        messageBuilder.append("Количество: ").append(order.getItems().get(0).getQuantity()).append(" шт.").append("\n");
        messageBuilder.append("Цена за шт.: ").append(formattedUnit).append("\n");
        messageBuilder.append("Сумма: ").append(formattedTotal).append("\n");
        messageBuilder.append("Статус: ожидает подтверждения");

        if (formattedTime != null) {
            messageBuilder.append("\nВремя бронирования: ").append(formattedTime);
        }

        messageBuilder.append("\n\nЗаказ закреплён за ").append(reserverName).append(". Мы сообщим заведению и пришлём уведомление, когда коробка будет готова к выдаче. Если появятся вопросы — используйте команду /help.");

        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                messageBuilder.toString(),
                null,
                null,
                null
        ));
    }

    private String formatPrice(double value) {
        return String.format(Locale.US, "%,.0f ₸", value).replace(',', ' ');
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(timestamp);
            return RESERVATION_TIME_FORMAT.format(parsed);
        } catch (Exception ex) {
            log.debug("Unable to parse reservation timestamp: {}", timestamp, ex);
            return null;
        }
    }

    private String ensureHttps(String url) {
        if (url == null || url.isBlank()) {
            return "https://miniapp.foodsave.kz";
        }
        if (url.startsWith("http://")) {
            return "https://" + url.substring(7);
        }
        return url;
    }

    private String generateOrderNumber() {
        int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return String.format("FS-%06d", random);
    }

    private String orUnknown(String value) {
        if (value == null || value.isBlank()) {
            return "не указано";
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReservationPayload(
            String action,
            Long productId,
            String productName,
            String storeName,
            int quantity,
            double unitPrice,
            double totalPrice,
            String timestamp,
            String message
    ) {
    }

    private record ReservationResult(boolean success, Order order, Product product, String errorMessage) {
    }

    private ReservationResult createReservationOrder(TelegramUser from, ReservationPayload payload) {
        if (payload.productId() == null) {
            return new ReservationResult(false, null, null, "Не удалось определить продукт для бронирования. Пожалуйста, обновите мини‑приложение и попробуйте снова.");
        }

        Product product = productRepository.findById(payload.productId())
                .orElse(null);

        if (product == null) {
            return new ReservationResult(false, null, null, "Выбранный продукт больше не доступен. Попробуйте выбрать другую коробку.");
        }

        if (!productService.hasSufficientStock(product.getId(), Math.max(payload.quantity(), 1))) {
            return new ReservationResult(false, null, product, "Упс! Коробка уже закончилась. Выберите, пожалуйста, другую позицию.");
        }

        User user = null;
        if (from != null && from.id() != null) {
            user = userRepository.findByTelegramUserId(from.id()).orElse(null);
        }

        if (user == null) {
            return new ReservationResult(false, null, product, "Не удалось найти ваш профиль. Откройте мини‑приложение FoodSave ещё раз через кнопку бота и повторите попытку.");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStore(product.getStore());
        order.setOrderNumber(generateUniqueOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setContactPhone(orFallbackPhone(user.getPhone()));
        order.setDeliveryAddress(product.getStore() != null ? product.getStore().getAddress() : null);
        order.setDeliveryNotes(payload.message() != null ? payload.message() : "Telegram бронирование");

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(Math.max(payload.quantity(), 1));

        BigDecimal unitPrice = payload.unitPrice() > 0
                ? BigDecimal.valueOf(payload.unitPrice())
                : (product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO);

        item.setUnitPrice(unitPrice);
        item.calculateTotalPrice();

        order.addItem(item);
        order.calculateTotals();

        try {
            productService.reduceStockQuantity(product.getId(), item.getQuantity());
        } catch (Exception ex) {
            log.error("Failed to reserve stock for product {}", product.getId(), ex);
            return new ReservationResult(false, null, product, "Не удалось забронировать коробку: остатков недостаточно. Попробуйте выбрать другой товар.");
        }

        Order savedOrder = orderRepository.save(order);

        return new ReservationResult(true, savedOrder, product, null);
    }

    private String generateUniqueOrderNumber() {
        String orderNumber;
        do {
            orderNumber = generateOrderNumber();
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    private String orFallbackPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "+7 000 000 0000";
        }
        return phone;
    }
}
