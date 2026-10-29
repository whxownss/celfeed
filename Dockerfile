#FROM eclipse-temurin:21-jdk
#
#COPY server-social/build/libs/server-social-0.0.1-SNAPSHOT.jar /server-social.jar
#
#ENTRYPOINT ["java", "-jar", "/server-social.jar"]


# 멀티스테이지 빌드
# 빌드용 이미지에서 jar를 만듦고
# 실행용 이미지에서 완성된 jar만 가져와서 최종 이미지를 작게 만드는 구조

# Gradle과 JDK 21이 설치된 이미지를 베이스로 사용
FROM gradle:9.2.1-jdk21 AS builder

# 이후 명령을 실행할 컨테이너 내부 작업 디렉터리
WORKDIR /app

# 의존성 캐시를 위해 빌드 설정 파일 먼저 복사 (빌드 설정은 잘 안 바뀌어서 먼저)
# !!! 모듈 추가/삭제 시 변경 필요 !!!
COPY settings.gradle build.gradle ./
COPY server-social/build.gradle server-social/
COPY server-notification/build.gradle server-notification/
COPY server-consumer/build.gradle server-consumer/
COPY server-batch/build.gradle server-batch/
COPY common-web/build.gradle common-web/
COPY common-event/build.gradle common-event/
COPY domain-social/build.gradle domain-social/
COPY domain-notification/build.gradle domain-notification/
COPY infra-sharding/build.gradle infra-sharding/

# 실행 과정에서 필요한 라이브러리 jar를 내려받아 Gradle 캐시에 저장
RUN gradle \
    # :dependencies \
    :server-social:dependencies \
    :server-notification:dependencies \
    :server-consumer:dependencies \
    :server-batch:dependencies \
    :common-web:dependencies \
    :common-event:dependencies \
    :domain-social:dependencies \
    :domain-notification:dependencies \
    :infra-sharding:dependencies \
    --no-daemon # || true

# 전체 소스 복사 후 지정 모듈만 빌드 (소스 코드는 자주 바뀌어서 나중에)
COPY . .
ARG MODULE
RUN gradle :${MODULE}:bootJar --no-daemon -x test

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

ARG MODULE
COPY --from=builder /app/${MODULE}/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]