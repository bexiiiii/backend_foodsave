package com.foodsave.backend.service;

import com.foodsave.backend.dto.NotificationDTO;
import com.foodsave.backend.dto.NotificationSettingsDTO;
import com.foodsave.backend.entity.Notification;
import com.foodsave.backend.entity.NotificationSettings;
import com.foodsave.backend.entity.User;
import com.foodsave.backend.exception.ResourceNotFoundException;
import com.foodsave.backend.repository.NotificationRepository;
import com.foodsave.backend.repository.NotificationSettingsRepository;
import com.foodsave.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final SecurityUtils securityUtils;

    public List<NotificationDTO> getAllNotifications() {
        User currentUser = securityUtils.getCurrentUser();
        return notificationRepository.findByUserOrderByCreatedAtDesc(currentUser).stream()
                .map(NotificationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Long getUnreadCount() {
        User currentUser = securityUtils.getCurrentUser();
        return notificationRepository.countByUserAndReadFalse(currentUser);
    }

    public NotificationDTO markAsRead(Long id) {
        User currentUser = securityUtils.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        
        notification.setRead(true);
        return NotificationDTO.fromEntity(notificationRepository.save(notification));
    }

    public void markAllAsRead() {
        User currentUser = securityUtils.getCurrentUser();
        List<Notification> unreadNotifications = notificationRepository.findByUserAndReadFalse(currentUser);
        unreadNotifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unreadNotifications);
    }

    public void deleteNotification(Long id) {
        User currentUser = securityUtils.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        notificationRepository.delete(notification);
    }

    public void deleteNotifications(List<Long> ids) {
        User currentUser = securityUtils.getCurrentUser();
        List<Notification> notifications = notificationRepository.findAllByIdInAndUser(ids, currentUser);
        notificationRepository.deleteAll(notifications);
    }

    public NotificationSettingsDTO getNotificationSettings() {
        User currentUser = securityUtils.getCurrentUser();
        NotificationSettings settings = settingsRepository.findByUser(currentUser)
                .orElseGet(() -> createDefaultSettings(currentUser));
        return NotificationSettingsDTO.fromEntity(settings);
    }

    public NotificationSettingsDTO updateNotificationSettings(NotificationSettingsDTO settingsDTO) {
        User currentUser = securityUtils.getCurrentUser();
        NotificationSettings settings = settingsRepository.findByUser(currentUser)
                .orElseGet(() -> createDefaultSettings(currentUser));

        settings.setEmailEnabled(settingsDTO.isEmailEnabled());
        settings.setPushEnabled(settingsDTO.isPushEnabled());
        settings.setSmsEnabled(settingsDTO.isSmsEnabled());
        settings.setOrderUpdates(settingsDTO.isOrderUpdates());
        settings.setPromotions(settingsDTO.isPromotions());
        settings.setSystemUpdates(settingsDTO.isSystemUpdates());

        return NotificationSettingsDTO.fromEntity(settingsRepository.save(settings));
    }

    private NotificationSettings createDefaultSettings(User user) {
        NotificationSettings settings = new NotificationSettings();
        settings.setUser(user);
        settings.setEmailEnabled(true);
        settings.setPushEnabled(true);
        settings.setSmsEnabled(false);
        settings.setOrderUpdates(true);
        settings.setPromotions(true);
        settings.setSystemUpdates(true);
        return settingsRepository.save(settings);
    }
} 