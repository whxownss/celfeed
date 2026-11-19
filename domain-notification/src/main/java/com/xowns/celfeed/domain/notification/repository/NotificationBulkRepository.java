package com.xowns.celfeed.domain.notification.repository;

import com.xowns.celfeed.domain.notification.common.snowflake.SnowflakeIdGenerator;
import com.xowns.celfeed.domain.notification.dto.NotificationBulkDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class NotificationBulkRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public NotificationBulkRepository(
            @Qualifier("notificationJdbcTemplate") JdbcTemplate jdbcTemplate,
            SnowflakeIdGenerator snowflakeIdGenerator) {

        this.jdbcTemplate = jdbcTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    public List<Long> batchInsert(List<NotificationBulkDTO> bulkList) {
        String sql = "insert into " +
                        " notification (id, event_id, receiver_id, actor_id, type, target_id, is_read, created_at) " +
                        " values (?, ?, ?, ?, ?, ?, 'N', ?)";
        LocalDateTime now = LocalDateTime.now();

        List<Long> result = new ArrayList<>();

        jdbcTemplate.batchUpdate(sql, bulkList, bulkList.size(), (ps, argument) -> {
            argument.setId(snowflakeIdGenerator.nextId());
            argument.setCreatedAt(now);

            ps.setLong(1, argument.getId());
            ps.setString(2, argument.getEventId());
            ps.setLong(3, argument.getReceiverId());
            ps.setLong(4, argument.getActorId());
            ps.setString(5, argument.getType());
            ps.setLong(6, argument.getTargetId());
            ps.setTimestamp(7, Timestamp.valueOf(argument.getCreatedAt()));

            result.add(argument.getId());
        });

        return result;
    }

    public List<Long> batchInsertIgnoreDuplicates(List<NotificationBulkDTO> bulkList) {
        String sql = """
                INSERT INTO notification(id, event_id, receiver_id, actor_id, type, target_id, is_read, created_at)
                VALUES(?, ?, ?, ?, ?, ?, 'N', ?)
                ON DUPLICATE KEY UPDATE id = id
        """;
        LocalDateTime now = LocalDateTime.now();

        List<Long> result = new ArrayList<>();

        jdbcTemplate.batchUpdate(sql, bulkList, bulkList.size(), (ps, arg) -> {
            arg.setId(snowflakeIdGenerator.nextId());
            arg.setCreatedAt(now);

            ps.setLong(1, arg.getId());
            ps.setString(2, arg.getEventId());
            ps.setLong(3, arg.getReceiverId());
            ps.setLong(4, arg.getActorId());
            ps.setString(5, arg.getType());
            ps.setLong(6, arg.getTargetId());
            ps.setTimestamp(7, Timestamp.valueOf(arg.getCreatedAt()));

            result.add(arg.getId());
        });

        return result;
    }
}

