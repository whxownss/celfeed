package com.xowns.celfeed.repository.notification;

import com.xowns.celfeed.common.snowflake.SnowflakeIdGenerator;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class NotificationBulkRepository {


    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public NotificationBulkRepository(
            @Qualifier("notification") JdbcTemplate jdbcTemplate,
            SnowflakeIdGenerator snowflakeIdGenerator) {

        this.jdbcTemplate = jdbcTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    public List<Long> batchInsert(List<NotificationBulkDTO> bulkList) {
        String sql = "insert into " +
                        " notification (id, receiver_id, actor_id, type, target_id, is_read, created_at) " +
                        " values (?, ?, ?, ?, ?, 'N', ?)";
        LocalDateTime now = LocalDateTime.now();

        List<Long> result = new ArrayList<>();

        jdbcTemplate.batchUpdate(sql, bulkList, bulkList.size(), (ps, argument) -> {
            argument.setId(snowflakeIdGenerator.nextId());
            argument.setCreatedAt(now);

            ps.setLong(1, argument.getId());
            ps.setLong(2, argument.getReceiverId());
            ps.setLong(3, argument.getActorId());
            ps.setString(4, argument.getType());
            ps.setLong(5, argument.getTargetId());
            ps.setTimestamp(6, Timestamp.valueOf(argument.getCreatedAt()));

            result.add(argument.getId());
        });

        return result;
    }
}

