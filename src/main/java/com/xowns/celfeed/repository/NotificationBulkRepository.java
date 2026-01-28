package com.xowns.celfeed.repository;

import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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

    public List<Long> batchInsert2(List<NotificationBulkDTO> bulkList) {
        String sql = "insert into" +
                " notification (receiver_id, actor_id, type, target_id, is_read, created_at) " +
                " values (?, ?, ?, ?, 'N', now())";

        List<Long> result = new ArrayList<>();

        jdbcTemplate.execute((Connection con) -> {
            try (PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                for (NotificationBulkDTO dto : bulkList) {
                    ps.setLong(1, dto.getReceiverId());
                    ps.setLong(2, dto.getActorId());
                    ps.setString(3, dto.getType());
                    ps.setLong(4, dto.getTargetId());
                    ps.addBatch();
                }
                ps.executeBatch();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    while (rs.next()) {
                        result.add(rs.getLong(1));
                    }
                }
                return null;
            }
        });

        return result;
    }
}
