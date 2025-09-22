package com.foodsave.backend.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        TelegramMessage message,
        TelegramCallbackQuery callbackQuery
) {
}
