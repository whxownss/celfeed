package com.xowns.celfeed.domain.social;

import com.xowns.celfeed.domain.social.config.SocialJpaConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * domain-social 은 실행 모듈이 아니라서 @SpringBootConfiguration 이 없다.
 * 실행 모듈(server-social 등)과 같은 방식으로 SocialJpaConfig 를 Import 한다.
 * (SocialJpaConfig 가 repository 패키지를 스캔하므로 QueryDSL 리포지토리도 함께 등록됨)
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SocialJpaConfig.class)
public class SocialTestApplication {
}
