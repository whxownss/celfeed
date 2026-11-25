package com.xowns.celfeed.infra.sharding.integration;

import com.xowns.celfeed.infra.sharding.config.NotificationJpaConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 실행 모듈(server-consumer, server-notification)과 같은 방식으로 NotificationJpaConfig 를 Import 한 테스트용 앱
 * (컴포넌트 스캔 범위를 좁히려고 @SpringBootApplication 대신 필요한 것만 지정)
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({NotificationJpaConfig.class, ShardedNotificationService.class})
class ShardingTestApplication {
}
