package com.xowns.celfeed.config.jpa;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.xowns.celfeed.repository.notification",
        entityManagerFactoryRef = "notificationEntityManagerFactory",
        transactionManagerRef = "notificationTransactionManager"
)
public class NotificationJpaConfig {

    @Qualifier("notification")
    @Bean
    @ConfigurationProperties("celfeed.jpa")
    public JpaProperties notificationJpaProperties() {
        return new JpaProperties();
    }

    @Qualifier("notification")
    @Bean
    public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
            @Qualifier("notification") DataSource dataSource, @Qualifier("notification") JpaProperties jpaProperties) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("com.xowns.celfeed.domain.notification");
        factory.setPersistenceUnitName("notification");
        factory.setJpaPropertyMap(jpaProperties.getProperties());

        return factory;
    }



    @Qualifier("notification")
    @Bean
    public PlatformTransactionManager notificationTransactionManager(
            @Qualifier("notification") EntityManagerFactory emf) {

        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(emf);
        return txManager;
    }
}

