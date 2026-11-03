package com.xowns.celfeed.server.social;

import com.xowns.celfeed.common.web.config.WebConfig;
import com.xowns.celfeed.domain.social.config.SocialJpaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({SocialJpaConfig.class, WebConfig.class})
@SpringBootApplication
public class SocialApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocialApplication.class, args);
	}
}
