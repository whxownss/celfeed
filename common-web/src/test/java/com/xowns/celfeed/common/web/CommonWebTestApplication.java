package com.xowns.celfeed.common.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * common-web 은 실행 모듈이 아니라서 @SpringBootConfiguration 이 없다.
 * @WebMvcTest 가 설정 클래스를 찾을 수 있도록 테스트 전용으로 둔다. (WebConfig 는 컴포넌트 스캔으로 등록됨)
 */
@SpringBootApplication
public class CommonWebTestApplication {
}
