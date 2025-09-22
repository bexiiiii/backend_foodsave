package com.foodsave.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodsave.backend.dto.telegram.TelegramCallbackQuery;
import com.foodsave.backend.dto.telegram.TelegramMessage;
import com.foodsave.backend.dto.telegram.TelegramUpdate;
import com.foodsave.backend.dto.telegram.TelegramUser;
import com.foodsave.backend.dto.telegram.TelegramWebAppData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookService {

    private final TelegramBotService telegramBotService;
    private final ObjectMapper objectMapper;

    @Value("${telegram.miniapp.base-url:https://miniapp.foodsave.kz}")
    private String miniAppBaseUrl;

    @Value("${telegram.support.username:@FoodSave_kz}")
    private String supportUsername;

    private static final DateTimeFormatter RESERVATION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("ru"));

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

        if (trimmed.startsWith("/start")) {
            sendWelcomeMessage(chatId);
            return;
        }

        if (trimmed.startsWith("/help")) {
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
        String orderNumber = generateOrderNumber();
        String reserverName = from != null ? from.displayName() : "вас";
        String formattedTotal = formatPrice(payload.totalPrice());
        String formattedUnit = formatPrice(payload.unitPrice());
        String formattedTime = formatTimestamp(payload.timestamp());

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("🧾 Заказ №").append(orderNumber).append("\n");
        messageBuilder.append("Коробка: ").append(orUnknown(payload.productName())).append("\n");
        messageBuilder.append("Магазин: ").append(orUnknown(payload.storeName())).append("\n");
        messageBuilder.append("Количество: ").append(payload.quantity()).append(" шт.").append("\n");
        messageBuilder.append("Цена за шт.: ").append(formattedUnit).append("\n");
        messageBuilder.append("Сумма: ").append(formattedTotal).append("\n\n");
        messageBuilder.append("Заказ забронирован для ").append(reserverName).append(".");

        if (formattedTime != null) {
            messageBuilder.append("\nВремя бронирования: ").append(formattedTime);
        }

        messageBuilder.append("\n\nМы свяжемся с заведением и напомним вам о заказе. Если появятся вопросы — команда поддержки поможет по команде /help.");

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
}
