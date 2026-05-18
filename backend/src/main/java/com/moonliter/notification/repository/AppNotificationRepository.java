package com.moonliter.notification.repository;

import com.moonliter.notification.domain.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
}
