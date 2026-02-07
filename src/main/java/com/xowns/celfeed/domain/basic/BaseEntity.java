package com.xowns.celfeed.domain.basic;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseCreateEntity {

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersistUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdateUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }
}
