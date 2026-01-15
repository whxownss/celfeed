package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Notification;
import com.xowns.celfeed.domain.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n join fetch n.actor where n.receiver = :receiver")
    Slice<Notification> findByReceiver(@Param("receiver") Member receiver, Pageable pageable);

    List<Notification> findByTypeAndTargetId(NotificationType type, Long targetId);
}
