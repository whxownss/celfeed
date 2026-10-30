# 멀티스테이지 빌드
# 빌드용 이미지에서 JAR를 만듦고
# 실행용 이미지에서 완성된 JAR만 가져와서 최종 이미지를 작게 만드는 구조

# ===============================================================
# [1. Build Stage]
# ===============================================================

# Gradle이 포함되지 않은 이미지이므로, Gradle Wrapper 사용해서 빌드
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
ARG MODULE

# Gradle Wrapper 및 루트 설정 파일 복사
COPY gradlew         ./
COPY gradle          gradle
COPY settings.gradle ./
COPY build.gradle    ./
RUN chmod +x gradlew

# 필요한 모듈의 build.gradle 복사 (빌드 설정은 잘 안 바뀌어서 먼저)
COPY server-social/build.gradle       server-social/
COPY server-notification/build.gradle server-notification/
COPY server-consumer/build.gradle     server-consumer/
COPY server-batch/build.gradle        server-batch/
COPY common-web/build.gradle          common-web/
COPY common-event/build.gradle        common-event/
COPY domain-social/build.gradle       domain-social/
COPY domain-notification/build.gradle domain-notification/
COPY infra-sharding/build.gradle      infra-sharding/

# 실행 과정에서 필요한 라이브러리 jar를 내려받아 Gradle 캐시에 저장
RUN ./gradlew :${MODULE}:dependencies --no-daemon

# 필요한 모듈의 소스코드 복사 (소스 코드는 자주 바뀌어서 나중에)
COPY server-social/src       server-social/src
COPY server-notification/src server-notification/src
COPY server-consumer/src     server-consumer/src
COPY server-batch/src        server-batch/src
COPY common-web/src          common-web/src
COPY common-event/src        common-event/src
COPY domain-social/src       domain-social/src
COPY domain-notification/src domain-notification/src
COPY infra-sharding/src      infra-sharding/src

# 실행 JAR 빌드
RUN ./gradlew :${MODULE}:bootJar --no-daemon -x test


# ===============================================================
# [2. Runtime Stage]
# ===============================================================
FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE

# 빌드된 JAR 파일 복사
COPY --from=builder /workspace/${MODULE}/build/libs/*SNAPSHOT.jar app.jar

# JAR 파일 실행
ENTRYPOINT ["java", "-jar", "app.jar"]