package com.xowns.celfeed.domain.notification;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * domain-notification 은 실행 모듈이 아니라서 @SpringBootConfiguration 이 없다.
 * @DataJpaTest 가 설정 클래스를 찾을 수 있도록 테스트 전용으로 둔다.
 */
@SpringBootApplication
public class NotificationTestApplication {
}
