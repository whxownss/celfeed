package com.xowns.celfeed.infra.sharding.aop;

import com.xowns.celfeed.infra.sharding.core.Sharding;
import com.xowns.celfeed.infra.sharding.holder.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Aspect
@Order(1) // 순서 유의
public class ServiceAspect {

    @Pointcut("execution(public * com.xowns.celfeed.server.consumer.service..Notification*Service.*(..))")
    private void service() {
    }

    @Around("service() && @within(sharding) && args(shardKey,..)")
    public Object handler(ProceedingJoinPoint pjp, Sharding sharding, long shardKey) throws Throwable {
        Signature signature = pjp.getSignature();

        UserHolder.setSharding(sharding.target(), shardKey);
        log.info("[Sharding AOP] 샤드키 설정 : 클래스={}, 메서드={}, 샤드키={}",
                signature.getDeclaringTypeName(), ((MethodSignature) signature).getMethod().getName(), shardKey);

        Object returnVal = pjp.proceed();

        UserHolder.clearSharding();
        log.info("[Sharding AOP] 샤드키 해제 : 클래스={}, 메서드={}, 샤드키={}",
                signature.getDeclaringTypeName(), ((MethodSignature) signature).getMethod().getName(), shardKey);

        return returnVal;
    }
}