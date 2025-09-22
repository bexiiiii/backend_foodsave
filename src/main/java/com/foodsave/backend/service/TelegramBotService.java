package com.foodsave.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotService {

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.miniapp.base-url:}")
    private String miniAppBaseUrl;

    private final RestTemplateBuilder restTemplateBuilder;

    private RestTemplate restTemplate;

    public record TelegramMessagePayload(String text, String imageUrl, String buttonText, String buttonUrl) {}

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(5))
                    .setReadTimeout(Duration.ofSeconds(5))
                    .build();
        }
        return restTemplate;
    }

    public void sendMessage(Long chatId, TelegramMessagePayload message) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token is not configured");
            return;
        }
        if (chatId == null) {
            log.warn("Cannot send Telegram message without chat id");
            return;
        }
        if (message == null || (message.text() == null && message.imageUrl() == null)) {
            log.warn("Telegram message payload is empty");
            return;
        }

        boolean hasImage = message.imageUrl() != null && !message.imageUrl().isBlank();
        String endpoint = hasImage ? "/sendPhoto" : "/sendMessage";
        String url = "https://api.telegram.org/bot" + botToken + endpoint;

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("parse_mode", "HTML");

        if (hasImage) {
            payload.put("photo", message.imageUrl());
            payload.put("caption", message.text());
        } else {
            payload.put("text", message.text());
        }

        if (message.buttonText() != null && message.buttonUrl() != null) {
            Map<String, Object> button = new HashMap<>();
            button.put("text", message.buttonText());

            String buttonUrl = message.buttonUrl();
            String miniAppUrl = ensureHttps(miniAppBaseUrl);
            if (miniAppUrl != null && buttonUrl.startsWith(miniAppUrl)) {
                button.put("web_app", Map.of("url", buttonUrl));
            } else {
                button.put("url", buttonUrl);
            }

            Map<String, Object> replyMarkup = Map.of(
                    "inline_keyboard",
                    List.of(List.of(button))
            );
            payload.put("reply_markup", replyMarkup);
        }

        try {
            ResponseEntity<String> response = getRestTemplate().postForEntity(url, payload, String.class);
            log.debug("Telegram sendMessage status: {}", response.getStatusCodeValue());
        } catch (Exception e) {
            log.error("Failed to send Telegram message", e);
        }
    }

    public void sendWebAppMessage(Long chatId, String text, String buttonText, String webAppUrl) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token is not configured");
            return;
        }
        if (chatId == null) {
            log.warn("Cannot send Telegram message without chat id");
            return;
        }
        if (text == null || text.isBlank()) {
            log.warn("Telegram message text is empty");
            return;
        }
        if (buttonText == null || buttonText.isBlank() || webAppUrl == null || webAppUrl.isBlank()) {
            log.warn("WebApp button requires non-empty text and url");
            return;
        }

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        payload.put("parse_mode", "HTML");

        Map<String, Object> button = new HashMap<>();
        button.put("text", buttonText);
        button.put("web_app", Map.of("url", webAppUrl));

        payload.put("reply_markup", Map.of(
                "inline_keyboard",
                List.of(List.of(button))
        ));

        try {
            ResponseEntity<String> response = getRestTemplate().postForEntity(url, payload, String.class);
            log.debug("Telegram sendWebAppMessage status: {}", response.getStatusCodeValue());
        } catch (Exception e) {
            log.error("Failed to send Telegram web app message", e);
        }
    }

    public String resolveButtonUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String base = ensureHttps(miniAppBaseUrl);
        if (base == null) {
            log.warn("Mini app base URL is not configured, returning raw button path");
            return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        }

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        return base + path;
    }

    private String ensureHttps(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://")) {
            return "https://" + trimmed.substring(7);
        }
        if (!trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }
}
