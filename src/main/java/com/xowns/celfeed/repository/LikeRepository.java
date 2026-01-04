package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {
}
