# [JPA] 샤딩 적용 과정에서 발생한 알림 엔티티의 연관관계 문제

<br>

## 1. 개요

> [알림 테이블에 샤딩을 적용](/docs/issues/issue6-0.md)하면서, 알림 DB를 따로 분리했고 샤드별 DB 접근을 위해 `DataSource`와 `EntityManagerFactory`를 별도의 빈으로 등록했습니다.
> 
> 하지만 `Association 'Notification.actor' targets the type 'Member' which does not belong to the same persistence unit` 메시지와 함께 애플리케이션이 실행되지 않는 문제가 발생했습니다.

<br>

## 2. 문제 분석

> 우선 알림 도메인용으로 등록한 `EntityManagerFactory`를 확인해 보겠습니다.

 <br>

**✅ NotificationJpaConfig**
```java
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(...)
public class NotificationJpaConfig {

    @Qualifier("notification")
    @Bean
    public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
            @Qualifier("notification") DataSource dataSource,
            @Qualifier("notification") JpaProperties jpaProperties
    ) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		        
        factory.setPackagesToScan("com.xowns.celfeed.domain.notification");
        ...
        return factory;
    }
    ...
}
```
- `factory.setPackagesToScan()`로 notification 패키지에 있는 엔티티만 관리하겠다고 설정했습니다.
- 이것이 왜 문제가 되는지는 알림 엔티티를 확인해 보겠습니다.

<br>

**✅ Notification 엔티티**
```java
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification extends BaseCreateEntity {
    ...
    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "receiver_id")
    private Member receiver;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "actor_id")
    private Member actor;
    ...
}
```
- 알림 엔티티는 회원(Member) 엔티티와 연관관계를 맺고 있습니다.
- `EntityManagerFactory`가 관리하는 notification 패키지에는 Member 엔티티가 존재하지 않습니다.

<br>

**✅ 예외 발생**
```
Caused by: org.hibernate.AnnotationException: Association 'com.xowns.celfeed.domain.notification.Notification.actor' targets the type 'com.xowns.celfeed.domain.basic.Member' which does not belong to the same persistence unit
```
- **Notification 엔티티와 다른 persistence unit에 속한 Member 엔티티를 연관관계**로 묶으려고 해서 예외가 발생한 것입니다.

<br>

➡️ 알림 엔티티와 회원 엔티티를 같은 패키지에 넣으면 어떻게 될까? 해당 예외에 대해서는 당장 해결되지만, 알림 DB에 Member 테이블이 없어서 결국 또 다른 예외가 발생합니다.

<br>

## 3. 해결 방법

> 소개할 두 가지 방식 모두 Member 엔티티와의 연관관계를 끊으면서 문제를 해결하는 방식입니다.
> 
> (참고로 클라이언트에 제공하는 알림 메시지에는 actor의 닉네임이 필요합니다.)

<br>

**✅ Member의 id를 필드로 갖기**
```java
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification extends BaseCreateEntity {
    ...
    private Long receiverId;
    private Long actorId;
    ...
}
```
- Member 엔티티와 연관관계를 맺는 것이 아닌 Member의 id를 직접 필드로 갖는 방식입니다.
- 엔티티와 연관관계를 맺을 때도 DB에는 엔티티의 id가 저장되기 때문에 DB 스키마는 변경되지 않습니다.
- 단점
    - 기존에는 페치 조인을 통해 알림을 조회함과 동시에 actor의 닉네임도 함께 조회했습니다.
    - 해당 방식에서는 객체 그래프 탐색을 하지 못하기 때문에 **닉네임을 따로 조회**해야 합니다.

<br>

**✅ Member의 닉네임을 컬럼으로 추가하기**
```java
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification extends BaseCreateEntity {
    ...
    private Long receiverId;
    private Long actorId;
    private String actorNickname;
    ...
}
```
- Member의 id를 직접 필드로 갖는 방식의 단점을 해결한 방식입니다.
- Notification 테이블에 닉네임 컬럼을 추가해서 알림 저장 시점에 actor의 닉네임도 함께 저장합니다.
- 단점
    - Member 테이블과 Notification 테이블에 데이터가 중복됩니다.
    - 닉네임이 변경될 경우 중복 저장된 데이터의 정합성이 깨질 수 있어, 별도의 동기화가 필요합니다.

<br>

➡️ 보통 알림 조회(id를 필드로 갖는 방식의 단점)가 닉네임 변경(닉네임을 컬럼으로 추가하는 방식의 단점)보다 더 자주 발생하지만, **중복 데이터의 동기화 비용과 향후 요구사항 변경 가능성**(알림 메시지에 닉네임이 아닌 이름 표시 등)을 고려해 **Member의 id를 필드로 갖는 방식**을 선택했습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed/tree/docs-readme#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)
