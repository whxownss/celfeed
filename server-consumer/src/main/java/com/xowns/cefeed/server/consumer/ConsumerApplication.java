package com.xowns.cefeed.server.consumer;

import com.xowns.celfeed.domain.social.config.SocialJpaConfig;
import com.xowns.celfeed.infra.sharding.config.NotificationJpaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({SocialJpaConfig.class, NotificationJpaConfig.class})
@SpringBootApplication
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
