package com.xowns.celfeed;

import com.xowns.celfeed.repository.notification.NotificationRepository;
import com.xowns.celfeed.repository.basic.FollowRepository;
import com.xowns.celfeed.repository.basic.LikeRepository;
import com.xowns.celfeed.repository.basic.MemberRepository;
import com.xowns.celfeed.repository.basic.PostRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class CelfeedApplication {

	public static void main(String[] args) {
		SpringApplication.run(CelfeedApplication.class, args);
	}

	@Bean
	@Profile("dev")
	public TestDataInit testDataInit(MemberRepository memberRepository,
									 PostRepository postRepository,
									 LikeRepository likeRepository,
									 FollowRepository followRepository,
									 NotificationRepository notificationRepository,
									 JdbcTemplate jdbcTemplate) {

		return new TestDataInit(
				memberRepository,
				postRepository,
				likeRepository,
				followRepository,
				notificationRepository,
				jdbcTemplate
		);
	}
}
