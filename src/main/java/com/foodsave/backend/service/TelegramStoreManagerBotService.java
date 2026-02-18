package com.foodsave.backend.service;

import com.foodsave.backend.domain.enums.TelegramSessionState;
import com.foodsave.backend.dto.telegram.TelegramMessage;
import com.foodsave.backend.dto.telegram.TelegramUpdate;
import com.foodsave.backend.dto.telegram.TelegramUser;
import com.foodsave.backend.entity.Product;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.entity.TelegramSession;
import com.foodsave.backend.entity.User;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.repository.StoreRepository;
import com.foodsave.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramStoreManagerBotService {

    private static final int PAGE_SIZE = 5;
    private static final Locale LOCALE = new Locale("ru");

    private final TelegramBotService telegramBotService;
    private final TelegramSessionService telegramSessionService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public boolean handleUpdate(TelegramUpdate update,
                                TelegramMessage message,
                                TelegramUser from,
                                Long chatId) {
        if (chatId == null) {
            return false;
        }
        boolean handled = false;
        if (update.callbackQuery() != null && update.callbackQuery().data() != null) {
            handled = handleCallback(chatId, update.callbackQuery().data());
        }
        if (handled) {
            return true;
        }
        if (message != null && message.text() != null) {
            return handleText(chatId, message.text().trim());
        }
        return false;
    }

    private boolean handleText(Long chatId, String text) {
        TelegramSession session = telegramSessionService.getOrCreate(chatId);
        if (text.isBlank()) {
            return false;
        }

        if (text.equalsIgnoreCase("/login")) {
            startLoginFlow(chatId, session);
            return true;
        }

        if (text.equalsIgnoreCase("/logout")) {
            telegramSessionService.resetSession(chatId);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Вы вышли из аккаунта. Чтобы войти снова, используйте команду /login.",
                    null,
                    null,
                    null
            ));
            return true;
        }

        if (text.equalsIgnoreCase("/products") || text.equalsIgnoreCase("мои товары")) {
            if (!requireAuthentication(chatId, session)) {
                return true;
            }
            sendProductList(chatId, session, 0);
            return true;
        }

        switch (session.getState()) {
            case AWAITING_EMAIL -> {
                session.setPendingEmail(text.trim().toLowerCase(Locale.ROOT));
                session.setState(TelegramSessionState.AWAITING_PASSWORD);
                telegramSessionService.save(session);
                telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                        "Введите пароль от личного кабинета FoodSave.",
                        null,
                        null,
                        null
                ));
                return true;
            }
            case AWAITING_PASSWORD -> {
                attemptLogin(chatId, session, text);
                return true;
            }
            case AWAITING_STOCK_INPUT -> {
                handleStockUpdate(chatId, session, text);
                return true;
            }
            case AWAITING_PRICE_INPUT -> {
                handlePriceUpdate(chatId, session, text);
                return true;
            }
            case AWAITING_ORIGINAL_PRICE_INPUT -> {
                handleOriginalPriceUpdate(chatId, session, text);
                return true;
            }
            default -> {
                // no-op
            }
        }

        if (session.isAuthenticated()) {
            sendDefaultMenu(chatId);
            return true;
        }

        return false;
    }

    private boolean handleCallback(Long chatId, String callbackData) {
        TelegramSession session = telegramSessionService.getOrCreate(chatId);
        if (callbackData.startsWith("SHOW_PRODUCTS")) {
            if (!requireAuthentication(chatId, session)) {
                return true;
            }
            int page = parseIntSafe(callbackData.split(":"), 1, 0);
            sendProductList(chatId, session, page);
            return true;
        }

        if (!session.isAuthenticated()) {
            sendAuthRequired(chatId);
            return true;
        }

        if (callbackData.startsWith("PRODUCT")) {
            String[] parts = callbackData.split(":");
            Long productId = parseLongSafe(parts, 1);
            int page = parseIntSafe(parts, 2, 0);
            if (productId != null) {
                sendProductDetails(chatId, session, productId, page);
            } else {
                telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                        "Не удалось определить товар. Попробуйте ещё раз.",
                        null,
                        null,
                        null
                ));
            }
            return true;
        }

        if (callbackData.startsWith("ACTION")) {
            String[] parts = callbackData.split(":");
            Long productId = parseLongSafe(parts, 1);
            String action = parts.length > 2 ? parts[2] : "";
            int page = parseIntSafe(parts, 3, 0);
            if (productId == null) {
                telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                        "Не удалось определить товар. Попробуйте ещё раз.",
                        null,
                        null,
                        null
                ));
                return true;
            }

            switch (action) {
                case "STOCK" -> promptForStock(chatId, session, productId, page);
                case "PRICE" -> promptForPrice(chatId, session, productId, page);
                case "ORIGINAL_PRICE" -> promptForOriginalPrice(chatId, session, productId, page);
                default -> telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                        "Неизвестное действие.",
                        null,
                        null,
                        null
                ));
            }
            return true;
        }

        return false;
    }

    private void startLoginFlow(Long chatId, TelegramSession session) {
        session.setState(TelegramSessionState.AWAITING_EMAIL);
        session.setPendingEmail(null);
        session.setPendingValueType(null);
        session.setPendingPage(null);
        session.setSelectedProductId(null);
        telegramSessionService.save(session);
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "Введите e-mail, который вы используете для входа в админку FoodSave.",
                null,
                null,
                null
        ));
    }

    private void attemptLogin(Long chatId, TelegramSession session, String password) {
        String email = session.getPendingEmail();
        if (email == null || email.isBlank()) {
            telegramSessionService.resetSession(chatId);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Сессия истекла. Отправьте команду /login для входа.",
                    null,
                    null,
                    null
            ));
            return;
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
            authentication.getPrincipal(); // Trigger validation
        } catch (BadCredentialsException ex) {
            // Сбрасываем состояние, чтобы пользователь мог начать заново
            telegramSessionService.resetSession(chatId);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Неверный e-mail или пароль. Попробуйте войти заново с помощью команды /login.",
                    null,
                    null,
                    null
            ));
            return;
        } catch (AuthenticationException ex) {
            log.error("Telegram login failed for email {}", email, ex);
            telegramSessionService.resetSession(chatId);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Не удалось выполнить вход. Попробуйте позже или обратитесь в поддержку.",
                    null,
                    null,
                    null
            ));
            return;
        }

        User user = userRepository.findByEmail(email)
                .orElse(null);
        if (user == null) {
            telegramSessionService.resetSession(chatId);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Пользователь не найден. Обратитесь к администратору.",
                    null,
                    null,
                    null
            ));
            return;
        }

        if (!user.isActive()) {
            telegramSessionService.resetSession(chatId);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Этот аккаунт отключён. Свяжитесь со службой поддержки.",
                    null,
                    null,
                    null
            ));
            return;
        }

        session.setState(TelegramSessionState.AUTHENTICATED);
        session.setUserId(user.getId());
        session.setPendingEmail(null);
        session.setPendingValueType(null);
        session.setSelectedProductId(null);
        session.setPendingPage(null);
        telegramSessionService.save(session);

        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "Добро пожаловать, " + user.getFirstName() + "! Теперь вы можете управлять товарами прямо здесь.",
                null,
                null,
                null
        ));
        sendDefaultMenu(chatId);
    }

    private void sendDefaultMenu(Long chatId) {
        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(callbackButton("📦 Мои товары", "SHOW_PRODUCTS:0"))
        );
        telegramBotService.sendMessageWithKeyboard(chatId,
                "Выберите действие:",
                keyboard);
    }

    private void sendAuthRequired(Long chatId) {
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "Для доступа к товарам необходимо войти. Отправьте команду /login.",
                null,
                null,
                null
        ));
    }

    private boolean requireAuthentication(Long chatId, TelegramSession session) {
        if (!session.isAuthenticated()) {
            sendAuthRequired(chatId);
            return false;
        }
        return true;
    }

    private void sendProductList(Long chatId, TelegramSession session, int page) {
        User user = loadSessionUser(session, chatId);
        if (user == null) {
            return;
        }

        Set<Long> storeIds = getManagedStoreIds(user);
        if (storeIds.isEmpty()) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "К вашему аккаунту пока не привязаны заведения. Свяжитесь с администратором.",
                    null,
                    null,
                    null
            ));
            return;
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("name").ascending());
        Page<Product> productPage = productRepository.findByStoreIdIn(storeIds, pageable);
        if (productPage.isEmpty()) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "У вас пока нет товаров. Добавьте их через админку.",
                    null,
                    null,
                    null
            ));
            return;
        }

        StringBuilder text = new StringBuilder("Ваши товары:\n\n");
        productPage.getContent().forEach(product -> {
            int stock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            text.append("• ")
                    .append(product.getName())
                    .append(" — ").append(formatPrice(product.getPrice()))
                    .append(" (остаток: ").append(stock).append(")\n");
        });

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        for (Product product : productPage.getContent()) {
            keyboard.add(List.of(callbackButton(
                    product.getName(),
                    "PRODUCT:" + product.getId() + ":" + page
            )));
        }

        List<Map<String, Object>> navigationRow = new ArrayList<>();
        if (page > 0) {
            navigationRow.add(callbackButton("⬅️ Назад", "SHOW_PRODUCTS:" + (page - 1)));
        }
        if (productPage.hasNext()) {
            navigationRow.add(callbackButton("Вперёд ➡️", "SHOW_PRODUCTS:" + (page + 1)));
        }
        if (!navigationRow.isEmpty()) {
            keyboard.add(navigationRow);
        }

        telegramBotService.sendMessageWithKeyboard(chatId, text.toString(), keyboard);
    }

    private void sendProductDetails(Long chatId, TelegramSession session, Long productId, int page) {
        Product product = loadAccessibleProduct(session, productId, chatId);
        if (product == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<b>").append(product.getName()).append("</b>\n");
        builder.append("Заведение: ").append(product.getStore().getName()).append("\n");
        builder.append("Цена со скидкой: ").append(formatPrice(product.getPrice())).append("\n");
        builder.append("Цена без скидки: ").append(formatPrice(product.getOriginalPrice())).append("\n");
        builder.append("Скидка: ").append(formatDiscount(product.getDiscountPercentage())).append("\n");
        int stock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        builder.append("Остаток: ").append(stock).append(" шт.\n");

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(callbackButton("Изменить остаток", "ACTION:" + productId + ":STOCK:" + page)));
        keyboard.add(List.of(callbackButton("Изменить цену со скидкой", "ACTION:" + productId + ":PRICE:" + page)));
        keyboard.add(List.of(callbackButton("Изменить цену без скидки", "ACTION:" + productId + ":ORIGINAL_PRICE:" + page)));
        keyboard.add(List.of(callbackButton("⬅️ К списку", "SHOW_PRODUCTS:" + page)));

        telegramBotService.sendMessageWithKeyboard(chatId, builder.toString(), keyboard);
    }

    private void promptForStock(Long chatId, TelegramSession session, Long productId, int page) {
        if (!prepareValueInput(session, productId, page, TelegramSessionState.AWAITING_STOCK_INPUT, "STOCK")) {
            sendForbiddenMessage(chatId);
            return;
        }
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "Введите новое количество (целое число).",
                null,
                null,
                null
        ));
    }

    private void promptForPrice(Long chatId, TelegramSession session, Long productId, int page) {
        if (!prepareValueInput(session, productId, page, TelegramSessionState.AWAITING_PRICE_INPUT, "PRICE")) {
            sendForbiddenMessage(chatId);
            return;
        }
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "Введите новую цену со скидкой (например, 2490.00).",
                null,
                null,
                null
        ));
    }

    private void promptForOriginalPrice(Long chatId, TelegramSession session, Long productId, int page) {
        if (!prepareValueInput(session, productId, page, TelegramSessionState.AWAITING_ORIGINAL_PRICE_INPUT, "ORIGINAL_PRICE")) {
            sendForbiddenMessage(chatId);
            return;
        }
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "Введите новую цену без скидки (например, 3990.00).",
                null,
                null,
                null
        ));
    }

    private boolean prepareValueInput(TelegramSession session,
                                      Long productId,
                                      int page,
                                      TelegramSessionState state,
                                      String valueType) {
        Product product = loadAccessibleProduct(session, productId, null);
        if (product == null) {
            return false;
        }
        session.setState(state);
        session.setPendingValueType(valueType);
        session.setSelectedProductId(productId);
        session.setPendingPage(page);
        telegramSessionService.save(session);
        return true;
    }

    private void handleStockUpdate(Long chatId, TelegramSession session, String payload) {
        Integer newQuantity = parseInteger(payload);
        if (newQuantity == null || newQuantity < 0) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Введите корректное целое число (0 или больше).",
                    null,
                    null,
                    null
            ));
            return;
        }
        Product product = loadAccessibleProduct(session, session.getSelectedProductId(), chatId);
        if (product == null) {
            return;
        }
        try {
            productService.updateStockQuantity(product.getId(), newQuantity);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Остаток обновлён: " + newQuantity + " шт.",
                    null,
                    null,
                    null
            ));
            resetToAuthenticated(session);
            sendProductDetails(chatId, session, product.getId(), safePage(session.getPendingPage()));
        } catch (IllegalArgumentException ex) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    ex.getMessage(),
                    null,
                    null,
                    null
            ));
        }
    }

    private void handlePriceUpdate(Long chatId, TelegramSession session, String payload) {
        BigDecimal newPrice = parseBigDecimal(payload);
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Введите положительное число. Пример: 1290 или 1290.50",
                    null,
                    null,
                    null
            ));
            return;
        }
        Product product = loadAccessibleProduct(session, session.getSelectedProductId(), chatId);
        if (product == null) {
            return;
        }
        try {
            BigDecimal originalPrice = product.getOriginalPrice();
            productService.updateProductPrices(product.getId(), originalPrice, newPrice);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Цена со скидкой обновлена: " + formatPrice(newPrice),
                    null,
                    null,
                    null
            ));
            resetToAuthenticated(session);
            sendProductDetails(chatId, session, product.getId(), safePage(session.getPendingPage()));
        } catch (IllegalArgumentException ex) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    ex.getMessage(),
                    null,
                    null,
                    null
            ));
        }
    }

    private void handleOriginalPriceUpdate(Long chatId, TelegramSession session, String payload) {
        BigDecimal newOriginalPrice = parseBigDecimal(payload);
        if (newOriginalPrice == null || newOriginalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Введите положительное число. Пример: 3490 или 3490.50",
                    null,
                    null,
                    null
            ));
            return;
        }
        Product product = loadAccessibleProduct(session, session.getSelectedProductId(), chatId);
        if (product == null) {
            return;
        }

        BigDecimal discounted = product.getPrice();
        try {
            productService.updateProductPrices(product.getId(), newOriginalPrice, discounted);
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    "Цена без скидки обновлена: " + formatPrice(newOriginalPrice),
                    null,
                    null,
                    null
            ));
            resetToAuthenticated(session);
            sendProductDetails(chatId, session, product.getId(), safePage(session.getPendingPage()));
        } catch (IllegalArgumentException ex) {
            telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                    ex.getMessage(),
                    null,
                    null,
                    null
            ));
        }
    }

    private Product loadAccessibleProduct(TelegramSession session, Long productId, Long chatId) {
        if (productId == null || session.getUserId() == null) {
            return null;
        }
        User user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null) {
            if (chatId != null) {
                telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                        "Пользователь не найден. Войдите заново командой /login.",
                        null,
                        null,
                        null
                ));
            }
            telegramSessionService.resetSession(session.getChatId());
            return null;
        }

        Set<Long> storeIds = getManagedStoreIds(user);
        if (storeIds.isEmpty()) {
            if (chatId != null) {
                sendForbiddenMessage(chatId);
            }
            return null;
        }
        return productRepository.findById(productId)
                .filter(product -> storeIds.contains(product.getStore().getId()))
                .orElseGet(() -> {
                    if (chatId != null) {
                        sendForbiddenMessage(chatId);
                    }
                    return null;
                });
    }

    private void sendForbiddenMessage(Long chatId) {
        telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                "У вас нет доступа к этому товару.",
                null,
                null,
                null
        ));
    }

    private User loadSessionUser(TelegramSession session, Long chatId) {
        if (session.getUserId() == null) {
            sendAuthRequired(chatId);
            return null;
        }
        return userRepository.findById(session.getUserId())
                .orElseGet(() -> {
                    telegramBotService.sendMessage(chatId, new TelegramBotService.TelegramMessagePayload(
                            "Пользователь не найден. Войдите заново через /login.",
                            null,
                            null,
                            null
                    ));
                    telegramSessionService.resetSession(chatId);
                    return null;
                });
    }

    private Set<Long> getManagedStoreIds(User user) {
        Set<Long> storeIds = new HashSet<>();
        if (user == null) {
            return storeIds;
        }
        storeRepository.findByOwnerId(user.getId()).forEach(store -> storeIds.add(store.getId()));
        storeRepository.findAllByManager(user).forEach(store -> storeIds.add(store.getId()));
        return storeIds;
    }

    private void resetToAuthenticated(TelegramSession session) {
        session.setState(TelegramSessionState.AUTHENTICATED);
        session.setPendingValueType(null);
        session.setSelectedProductId(null);
        session.setPendingPage(null);
        telegramSessionService.save(session);
    }

    private int safePage(Integer pendingPage) {
        return pendingPage != null ? pendingPage : 0;
    }

    private Integer parseInteger(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String raw) {
        try {
            String normalized = raw.replace(",", ".").trim();
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private int parseIntSafe(String[] parts, int index, int defaultValue) {
        if (parts.length <= index) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Long parseLongSafe(String[] parts, int index) {
        if (parts.length <= index) {
            return null;
        }
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatPrice(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        NumberFormat formatter = NumberFormat.getNumberInstance(LOCALE);
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(value) + " ₸";
    }

    private String formatDiscount(Double discount) {
        if (discount == null || discount <= 0) {
            return "—";
        }
        return String.format(Locale.US, "%.0f%%", discount);
    }

    private Map<String, Object> callbackButton(String text, String callbackData) {
        Map<String, Object> button = new HashMap<>();
        button.put("text", text);
        button.put("callback_data", callbackData);
        return button;
    }
}
