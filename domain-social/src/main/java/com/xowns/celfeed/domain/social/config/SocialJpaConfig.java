package com.xowns.celfeed.domain.social.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@ComponentScan(basePackages = "com.xowns.celfeed.domain.social.repository") // @Repository 스캔
@EnableJpaRepositories(
        basePackages = "com.xowns.celfeed.domain.social.repository",
        entityManagerFactoryRef = "socialEntityManagerFactory",
        transactionManagerRef = "socialTransactionManager"
)
public class SocialJpaConfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.social")
    public DataSource socialDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean socialEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("socialDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties
    ) {
        Map<String, Object> properties = hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(),
                new HibernateSettings()
        );

        return builder
                .dataSource(dataSource)
                .packages("com.xowns.celfeed.domain.social.entity")
                .persistenceUnit("social")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager socialTransactionManager(
            @Qualifier("socialEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
