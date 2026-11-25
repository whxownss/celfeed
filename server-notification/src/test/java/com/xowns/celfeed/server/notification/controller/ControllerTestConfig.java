package com.xowns.celfeed.server.notification.controller;

import com.xowns.celfeed.common.web.config.WebConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

/**
 * @WebMvcTest 용 설정
 * NotificationApplication 은 JPA 설정(NotificationJpaConfig, SocialJpaConfig)까지 Import 하므로,
 * 컨트롤러 테스트에서는 이 클래스가 먼저 발견되도록 같은 패키지에 두고 웹 설정만 올린다.
 * (컴포넌트 스캔을 하지 않으므로 컨트롤러도 직접 Import)
 */
@SpringBootConfiguration
@Import({WebConfig.class, NotificationController.class})
class ControllerTestConfig {
}
