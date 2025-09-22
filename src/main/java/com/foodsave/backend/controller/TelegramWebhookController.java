package com.foodsave.backend.controller;

import com.foodsave.backend.dto.telegram.TelegramUpdate;
import com.foodsave.backend.service.TelegramWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramWebhookService telegramWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody TelegramUpdate update) {
        telegramWebhookService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }
}
