package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Notification;
import com.xowns.celfeed.domain.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n join fetch n.actor where n.receiver = :receiver")
    Slice<Notification> findByReceiver(@Param("receiver") Member receiver, Pageable pageable);

    @Query("select n from Notification n join fetch n.actor where n.receiver = :receiver and n.createdAt >= :createdAt")
    Slice<Notification> findByReceiver(@Param("receiver") Member receiver,@Param("createdAt")  LocalDateTime createdAt, Pageable pageable);

    @EntityGraph(attributePaths = {"actor"})
    List<Notification> findByTypeAndTargetId(NotificationType type, Long targetId);

    @EntityGraph(attributePaths = {"actor"})
    List<Notification> findByIdIn(List<Long> generatedKeys);
}
