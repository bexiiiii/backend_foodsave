package com.foodsave.backend.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        Long messageId,
        TelegramUser from,
        TelegramChat chat,
        String text,
        TelegramWebAppData webAppData
) {
}
