package com.xowns.celfeed.repository;

import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    public void batchInsert(List<NotificationBulkDTO> bulkList) {
        String sql = "insert into" +
                        " notification (receiver_id, actor_id, type, target_id, is_read, created_at) " +
                        " values (?, ?, ?, ?, 'N', now())";
        jdbcTemplate.batchUpdate(sql, bulkList, bulkList.size(), (ps, argument) -> {
            ps.setLong(1, argument.getReceiverId());
            ps.setLong(2, argument.getActorId());
            ps.setString(3, argument.getType());
            ps.setLong(4, argument.getTargetId());
        });
    }
}
