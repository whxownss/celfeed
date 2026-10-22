package com.xowns.celfeed.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan(basePackages = "com.xowns.celfeed.domain.entity.basic")
@EnableJpaRepositories(basePackages = "com.xowns.celfeed.domain.repository.basic")
@SpringBootApplication(scanBasePackages = {"com.xowns.celfeed.app", "com.xowns.celfeed.domain.repository.basic"})
public class CelfeedApplication {

	public static void main(String[] args) {
		SpringApplication.run(CelfeedApplication.class, args);
	}
}
