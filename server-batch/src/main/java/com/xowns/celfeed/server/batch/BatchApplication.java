package com.xowns.celfeed.server.batch;

import com.xowns.celfeed.infra.sharding.config.NotificationDataSourceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Import;

@Import(NotificationDataSourceConfig.class)
@SpringBootApplication(exclude = DataJpaRepositoriesAutoConfiguration.class)
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}
