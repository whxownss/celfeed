package com.xowns.celfeed;

import com.xowns.celfeed.repository.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class CelfeedApplication {

	public static void main(String[] args) {
		SpringApplication.run(CelfeedApplication.class, args);
	}

	@Bean
	@Profile("develop")
	public TestDataInit testDataInit(MemberRepository memberRepository, PostRepository postRepository, LikeRepository likeRepository, FollowRepository followRepository, NotificationRepository notificationRepository) {
		return new TestDataInit(memberRepository, postRepository, likeRepository, followRepository, notificationRepository);
	}
}
