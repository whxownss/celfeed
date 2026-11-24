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

        // 샤딩 라우터는 부팅 시점에 커넥션을 줄 수 없으므로 메타데이터 조회를 항상 끈다
        properties.put("hibernate.boot.allow_jdbc_metadata_access", false);
        // yml에 dialect가 없으면 기본값 사용 (yml 값이 있으면 그쪽이 우선)
        properties.putIfAbsent("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

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
