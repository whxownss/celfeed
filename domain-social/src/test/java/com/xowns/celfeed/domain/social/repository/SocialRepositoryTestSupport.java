package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Map;

/**
 * 소셜 DB(MySQL) 리포지토리 테스트 공통 설정
 * <ul>
 *     <li>운영과 같은 이미지(mysql:8.4), 타임존, 스키마(docker/mysql/social/init.sql), JPA 네이밍 전략 사용</li>
 *     <li>운영과 같은 SocialJpaConfig 로 DataSource / EntityManagerFactory / 트랜잭션 매니저 구성</li>
 *     <li>컨테이너는 JVM 당 한 번만 띄우는 싱글톤 (@Container 를 쓰면 클래스마다 내려가서 캐시된 컨텍스트가 깨짐)</li>
 *     <li>@DataJpaTest 라서 각 테스트는 끝나면 롤백된다</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class SocialRepositoryTestSupport {

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("celfeed_social")
            .withUsername("root")
            .withPassword("root")
            .withEnv("TZ", "Asia/Seoul")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../docker/mysql/social/init.sql"), // 모듈 디렉터리 기준
                    "/docker-entrypoint-initdb.d/init.sql"
            )
            .withTmpFs(Map.of("/var/lib/mysql", "rw")) // Windows Docker 파일 I/O 가 느려서 메모리 사용
            .withStartupTimeout(Duration.ofMinutes(5));

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.social.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.social.username", MYSQL::getUsername);
        registry.add("spring.datasource.social.password", MYSQL::getPassword);
        registry.add("spring.datasource.social.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.social.maximum-pool-size", () -> 3);
        registry.add("spring.datasource.social.minimum-idle", () -> 1);

        // 서버 모듈 application.yaml 과 동일
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.hibernate.naming.implicit-strategy",
                () -> "org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl");
        registry.add("spring.jpa.hibernate.naming.physical-strategy",
                () -> "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
    }

    @Autowired
    protected EntityManager em;

    private int sequence = 0;

    // ========================================================================================
    // 테스트 데이터 헬퍼
    // ========================================================================================

    protected Member member(String nickname, MemberRole role) {
        Member member = Member.create(nickname, nickname + "@test.com", "password", role);
        em.persist(member);
        return member;
    }

    protected Member member() {
        return member("member" + (++sequence), MemberRole.FAN);
    }

    protected Post post(Member writer, String content) {
        Post post = Post.create(writer, content);
        em.persist(post);
        return post;
    }

    protected Like like(Post post, Member member) {
        Like like = Like.create(post, member);
        em.persist(like);
        return like;
    }

    protected Follow follow(Member from, Member to) {
        Follow follow = Follow.create(from, to);
        em.persist(follow);
        return follow;
    }

    /** 쓰기 지연된 SQL 을 DB 에 반영하고, 1차 캐시를 비워 다음 조회가 DB 에서 일어나게 함 */
    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}
