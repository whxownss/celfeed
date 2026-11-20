-- mysqld-exporter 계정 생성 및 권한 부여
CREATE USER 'exporter'@'%' IDENTIFIED BY 'exporter_pw';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;

-- 테이블 생성

USE celfeed_social;

CREATE TABLE member (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    nickname   VARCHAR(20)  NOT NULL,
    email      VARCHAR(100) NOT NULL,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(10)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_member_nickname UNIQUE (nickname),
    CONSTRAINT uk_member_email    UNIQUE (email)
) ENGINE=InnoDB;


CREATE TABLE follow (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    from_id    BIGINT      NOT NULL,
    to_id      BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_follow_from    FOREIGN KEY (from_id) REFERENCES member (id),
    CONSTRAINT fk_follow_to      FOREIGN KEY (to_id)   REFERENCES member (id),
    CONSTRAINT uk_follow_from_to UNIQUE (from_id, to_id),
    INDEX idx_follow_to_from (from_id, to_id)
) ENGINE=InnoDB;


CREATE TABLE post (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    content    VARCHAR(255) NOT NULL,
    is_deleted VARCHAR(1)   NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_post_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_post_member_deleted_created (member_id, is_deleted, created_at DESC)
) ENGINE=InnoDB;


CREATE TABLE likes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_likes_post        FOREIGN KEY (post_id)   REFERENCES post (id),
    CONSTRAINT fk_likes_member      FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT uk_likes_post_member UNIQUE (post_id, member_id),
    INDEX idx_likes_member_created_post (member_id, created_at DESC, post_id)
) ENGINE=InnoDB;


CREATE TABLE outbox_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_id      CHAR(36)     NOT NULL,
    event_type    VARCHAR(20)  NOT NULL,
    aggregate_id  BIGINT       NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    published_at  DATETIME(6),
    retry_count   INT          NOT NULL DEFAULT 0,
    retry_at      DATETIME(6)  NOT NULL,
    last_error    VARCHAR(255),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,

    CONSTRAINT uk_outbox_event UNIQUE (event_id),
    INDEX idx_outbox_status_craeted (status, created_at)
) ENGINE=InnoDB;