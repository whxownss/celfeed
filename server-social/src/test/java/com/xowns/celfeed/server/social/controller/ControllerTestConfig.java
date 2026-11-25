package com.xowns.celfeed.server.social.controller;

import com.xowns.celfeed.common.web.config.WebConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

/**
 * @WebMvcTest 용 설정
 * SocialApplication 은 SocialJpaConfig 까지 Import 하므로, 이 클래스가 먼저 발견되도록 같은 패키지에 두고 웹 설정만 올린다.
 * (컴포넌트 스캔을 하지 않으므로 컨트롤러도 직접 Import)
 */
@SpringBootConfiguration
@Import({WebConfig.class, MemberController.class, PostController.class, FollowController.class})
class ControllerTestConfig {
}
