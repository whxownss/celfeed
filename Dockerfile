FROM eclipse-temurin:21-jdk

COPY server-social/build/libs/server-social-0.0.1-SNAPSHOT.jar /server-social.jar

ENTRYPOINT ["java", "-jar", "/server-social.jar"]