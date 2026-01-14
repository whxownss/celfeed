package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n join fetch n.actor where n.receiver = :receiver")
    Slice<Notification> findAllByReceiver(@Param("receiver") Member receiver, Pageable pageable);
}
