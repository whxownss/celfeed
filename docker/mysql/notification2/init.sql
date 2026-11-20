-- mysqld-exporter 계정 생성 및 권한 부여
CREATE USER 'exporter'@'%' IDENTIFIED BY 'exporter_pw';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;

USE celfeed_notification2;

CREATE TABLE notification (
    id          BIGINT      NOT NULL,
    event_id    CHAR(36)    NOT NULL,
    receiver_id BIGINT      NOT NULL,
    actor_id    BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL,
    target_id   BIGINT      NOT NULL,
    is_read     VARCHAR(1)  NOT NULL,
    created_at  DATETIME(6) NOT NULL,

    PRIMARY KEY (id, created_at),
    CONSTRAINT uk_notification_event_receiver UNIQUE (event_id, receiver_id),
    INDEX idx_notification_receiver_created (receiver_id, created_at DESC)
) ENGINE=InnoDB
PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')),
    PARTITION p202702 VALUES LESS THAN (TO_DAYS('2027-03-01')),
    PARTITION p202703 VALUES LESS THAN (TO_DAYS('2027-04-01')),
    PARTITION pmax    VALUES LESS THAN MAXVALUE
);