package com.xowns.celfeed.domain.notification.common.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Transactional(transactionManager = "notificationTransactionManager")
public @interface NotificationTransactional {

    @AliasFor(annotation = Transactional.class, attribute = "readOnly")
    boolean readOnly() default false;
}
