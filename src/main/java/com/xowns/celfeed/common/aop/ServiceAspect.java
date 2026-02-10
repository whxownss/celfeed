package com.xowns.celfeed.common.aop;

import com.xowns.celfeed.config.sharding.Sharding;
import com.xowns.celfeed.config.sharding.UserHolder;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Order(1) // 순서 유의
public class ServiceAspect {

    @Pointcut("execution(public * com.xowns.celfeed.service.notification..Notification*Service.*(..))")
    private void service() {
    }

    @Around("service() && @within(sharding) && args(shardKey,..)")
    public Object handler(ProceedingJoinPoint pjp, Sharding sharding, long shardKey) throws Throwable {
        UserHolder.setSharding(sharding.target(), shardKey);
        Object returnVal = pjp.proceed();
        UserHolder.clearSharding();

        return returnVal;
    }
}