package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
