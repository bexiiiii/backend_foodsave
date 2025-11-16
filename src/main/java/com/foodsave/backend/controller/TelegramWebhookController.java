package com.foodsave.backend.controller;

import com.foodsave.backend.dto.telegram.TelegramUpdate;
import com.foodsave.backend.service.TelegramWebhookService;
import com.foodsave.backend.service.TelegramStoreManagerBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramWebhookService telegramWebhookService;
    private final TelegramStoreManagerBotService telegramStoreManagerBotService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody TelegramUpdate update) {
        log.info("Client bot webhook received");
        telegramWebhookService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/webhook/manager")
    public ResponseEntity<Void> handleManagerWebhook(@RequestBody TelegramUpdate update) {
        log.info("Manager bot webhook received");
        
        if (update == null) {
            log.warn("Received null update");
            return ResponseEntity.ok().build();
        }
        
        log.info("Manager bot update: message={}, callback={}",
            update.message() != null,
            update.callbackQuery() != null);
        
        com.foodsave.backend.dto.telegram.TelegramMessage message = resolveMessage(update);
        com.foodsave.backend.dto.telegram.TelegramUser from = resolveUser(update);
        Long chatId = resolveChatId(message);
        
        if (chatId == null) {
            log.warn("No chatId in manager bot update");
            return ResponseEntity.ok().build();
        }
        
        telegramStoreManagerBotService.handleUpdate(update, message, from, chatId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/webhook")
    public ResponseEntity<String> webhookHealthcheck() {
        return ResponseEntity.ok("Telegram webhook endpoint is ready");
    }
    
    @GetMapping("/webhook/manager")
    public ResponseEntity<String> managerWebhookHealthcheck() {
        return ResponseEntity.ok("Telegram manager webhook endpoint is ready");
    }
    
    private com.foodsave.backend.dto.telegram.TelegramMessage resolveMessage(TelegramUpdate update) {
        if (update.message() != null) {
            return update.message();
        }
        com.foodsave.backend.dto.telegram.TelegramCallbackQuery callbackQuery = update.callbackQuery();
        if (callbackQuery != null) {
            return callbackQuery.message();
        }
        return null;
    }

    private com.foodsave.backend.dto.telegram.TelegramUser resolveUser(TelegramUpdate update) {
        if (update.message() != null && update.message().from() != null) {
            return update.message().from();
        }
        com.foodsave.backend.dto.telegram.TelegramCallbackQuery callbackQuery = update.callbackQuery();
        if (callbackQuery != null) {
            if (callbackQuery.webAppData() != null && callbackQuery.from() != null) {
                return callbackQuery.from();
            }
            com.foodsave.backend.dto.telegram.TelegramMessage message = callbackQuery.message();
            if (message != null) {
                return message.from();
            }
        }
        return null;
    }

    private Long resolveChatId(com.foodsave.backend.dto.telegram.TelegramMessage message) {
        if (message != null && message.chat() != null) {
            return message.chat().id();
        }
        return null;
    }
}
