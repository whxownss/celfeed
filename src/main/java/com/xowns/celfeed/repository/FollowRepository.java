package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {
}
