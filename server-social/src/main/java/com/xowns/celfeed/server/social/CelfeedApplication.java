package com.xowns.celfeed.server.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan(basePackages = "com.xowns.celfeed.domain.social.entity")
@EnableJpaRepositories(basePackages = "com.xowns.celfeed.domain.social.repository")
@SpringBootApplication(scanBasePackages = {
		"com.xowns.celfeed.server.social", 				// 현재 프로젝트
		"com.xowns.celfeed.domain.social.repository" 	// @Repository
})
public class CelfeedApplication {

	public static void main(String[] args) {
		SpringApplication.run(CelfeedApplication.class, args);
	}
}
