package com.xowns.celfeed.domain.notification.repository;

import com.xowns.celfeed.domain.notification.common.snowflake.SnowflakeIdGenerator;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;

/**
 * 알림 DB(MySQL) 리포지토리 테스트 공통 설정
 * <ul>
 *     <li>운영과 같은 이미지(mysql:8.4), 타임존(Asia/Seoul), 스키마(docker/mysql/notification1/init.sql)를 사용</li>
 *     <li>컨테이너는 JVM 당 한 번만 띄우는 싱글톤 (종료는 Testcontainers 가 JVM 종료 시 처리)</li>
 *     <li>@DataJpaTest 라서 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백된다 (JdbcTemplate 도 같은 트랜잭션 사용)</li>
 * </ul>
 * 주의 : @Testcontainers + @Container 를 쓰면 테스트 클래스가 끝날 때마다 컨테이너가 내려가는데,
 * Spring 은 캐시한 컨텍스트(이전 컨테이너 포트)를 재사용하므로 다음 테스트 클래스가 접속에 실패한다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        NotificationBulkRepository.class,
        SnowflakeIdGenerator.class,
        NotificationRepositoryTestSupport.NotificationJdbcConfig.class
})
abstract class NotificationRepositoryTestSupport {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("celfeed_notification1")
            .withUsername("root")
            .withPassword("root")
            .withEnv("TZ", "Asia/Seoul")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../docker/mysql/notification1/init.sql"), // 모듈 디렉터리 기준
                    "/docker-entrypoint-initdb.d/init.sql"
            );

    static {
        MYSQL.start();
    }

    /** NotificationBulkRepository 가 @Qualifier("notificationJdbcTemplate") 로 주입받는다 */
    @TestConfiguration
    static class NotificationJdbcConfig {
        @Bean
        JdbcTemplate notificationJdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
