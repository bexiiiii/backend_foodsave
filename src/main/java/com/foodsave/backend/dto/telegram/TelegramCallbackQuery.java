package com.foodsave.backend.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackQuery(
        String id,
        TelegramUser from,
        TelegramMessage message,
        TelegramWebAppData webAppData
) {
}
