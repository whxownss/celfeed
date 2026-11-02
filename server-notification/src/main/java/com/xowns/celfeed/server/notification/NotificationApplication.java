package com.xowns.celfeed.server.notification;

import com.xowns.celfeed.common.web.config.WebConfig;
import com.xowns.celfeed.domain.social.config.SocialJpaConfig;
import com.xowns.celfeed.infra.sharding.config.NotificationJpaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({NotificationJpaConfig.class, SocialJpaConfig.class, WebConfig.class})
@SpringBootApplication
public class NotificationApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationApplication.class, args);
	}
}
