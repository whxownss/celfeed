package com.xowns.celfeed.infra.sharding.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@Import(NotificationDataSourceConfig.class)
@ComponentScan(basePackages = {
        "com.xowns.celfeed.domain.notification.repository",      // @Repository 스캔
        "com.xowns.celfeed.domain.notification.common.snowflake" // @Component 스캔
})
@EnableJpaRepositories(
        basePackages = "com.xowns.celfeed.domain.notification.repository",
        entityManagerFactoryRef = "notificationEntityManagerFactory",
        transactionManagerRef = "notificationTransactionManager"
)
public class NotificationJpaConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("notificationDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {

        Map<String, Object> properties = hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(),
                new HibernateSettings()
        );

        return builder
                .dataSource(dataSource)
                .packages("com.xowns.celfeed.domain.notification.entity")
                .persistenceUnit("notification")
                .properties(properties)
                .build();
    }

    @Bean
    public PlatformTransactionManager notificationTransactionManager(
            @Qualifier("notificationEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
