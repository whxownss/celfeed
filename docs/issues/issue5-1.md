# [JPA] 복합 키와 @GeneratedValue를 함께 사용할 수 없는 제약

<br>

## 1. 개요

> [파티션을 적용하면서](/docs/issues/issue5-0.md) 알림 테이블의 PK를 **복합 키**(id, created_at)로 구성하게 되었습니다.
> 
> 이에 맞게 엔티티도 수정했으나 `Identity generation isn't supported for composite ids`라는 메시지와 함께 예외가 발생했습니다.

<br>

## 2. 문제 분석

> 우선 변경된 테이블에 맞게 복합 키를 엔티티에 어떻게 매핑했는지 살펴보겠습니다.

<br>

**✅ Notification 엔티티**
```java
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification  {
		
    @EmbeddedId
    private NotificationId id;
    ...
}

@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class NotificationId implements Serializable {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime createdAt;
    ...
}
```
- 복합 키 중에서 id 컬럼은 auto_increment로 값을 생성하기 때문에 기존과 마찬가지로 `IDENTITY` 키 생성 전략을 선택했습니다.

<br>

**✅ 예외 발생**
```
Caused by: org.hibernate.id.IdentifierGenerationException: Identity generation isn't supported for composite ids
```
- `spring.jpa.hibernate.ddl-auto=validate` 속성을 설정하고 애플리케이션을 실행했을 때 위와 같은 예외가 발생했습니다.
- 복합 키에는 키 생성 전략으로 `IDENTITY`를 사용할 수 없다는 내용입니다.
- 왜 사용할 수 없을까?
    - `IDENTITY`는 DB의 auto_increment를 사용하는 전략으로 insert를 해야 id 값을 알 수 있습니다.
    - 복합 키에 사용되는 `@IdClass`나 `@EmbeddedId`는 모든 값이 채워져 있어야 JPA가 영속성 컨텍스트에서 엔티티를 관리할 수 있습니다.
    - 따라서 이 두 개념이 충돌하게 되어 함께 사용할 수 없는 것입니다.

<br>

## 3. 해결 방법

> 문제를 해결하기 위해서는 `IDENTITY` 전략과 `@EmbeddedId` 중 하나를 포기해야 합니다.

<br>

**✅ @EmbeddedId를 포기하고 id 컬럼만 매핑하는 방식**
```java
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification  {
		
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime createdAt;
    ...
}
```
- 복합 키로 변경한 PK에 맞게 `@EmbeddedId`로 매핑하는 것이 아니라, id 컬럼만 단일 PK인 것처럼 매핑했습니다.
- JPA의 `@Id`는 영속성 컨텍스트 내에서 엔티티를 식별할 때 사용되는데, 테이블의 PK가 복합 키라 하더라도 엔티티를 유일하게 식별할 수 있으면 id 필드에만 `@Id`를 지정해 줘도 문제가 없습니다.
- id 컬럼은 auto_increment로 설정되어 있기 때문에 insert되는 행마다 id 값이 전부 달라, 영속성 컨텍스트에서 id 필드만으로도 엔티티를 식별할 수 있습니다.

<br>

**✅ IDENTITY** **전략을 포기하고** **복합 키 모두 매핑하는 방식**
```java
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification  {
		
    @EmbeddedId
    private NotificationId id;
    ...
}

@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class NotificationId implements Serializable {

    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private LocalDateTime createdAt;
    ...
}
```
- 복합 키와 매핑하기 위해 `IDENTITY` 키 생성 전략을 사용하지 않고 `UUID` 전략을 사용했습니다.
- 해당 전략을 사용하면 auto_increment에 더 이상 의존하지 않고 엔티티가 영속화될 때 UUID를 자동으로 생성해 id 필드에 할당합니다.
- 물론 이 경우 DB 스키마 변경뿐만 아니라 **애플리케이션 로직 전반에 영향**이 있습니다.

<br>

➡️ 복합 키를 모두 매핑하는 방식의 비용을 고려해서, **파티션을 도입하기 전과 동일하게** 엔티티를 사용할 수 있는 **id 컬럼만 매핑**하는 방식을 선택하기로 결정했습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed/tree/docs-readme#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)
