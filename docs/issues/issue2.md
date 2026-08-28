# 알림 생성 서버와 조회 서버의 분리 : 장애 전파 차단

<br>

## 1. 개요
![issue2-1](/docs/img/issue2-1.png)

- 현재 **알림 생성**과 **알림 조회**가 **하나의 서버**에서 함께 처리되고 있습니다.
- 이 경우 알림 생성에 문제가 발생하면, 알림 조회에 어떠한 영향을 미치는지 확인해 보겠습니다.

<br>

## 2. 문제 상황
> 셀럽이 게시글을 작성하면 해당 셀럽을 팔로우한 팬들에게 알림이 전송됩니다.

<br>

✅ **nGrinder Test Script**
```groovy
@Test
public void test() {
    HTTPResponse loginResponse = request.POST("/api/members/login", ["id":"celeb10", "password":"1234123"])
    assertThat(loginResponse.statusCode, is(200))
    
    HTTPResponse writePostResponse = request.POST("/api/posts", ["content":"알림 테스트용 게시글 작성"])
    assertThat(writePostResponse.statusCode, is(201))
}
```
- 팔로워 수가 1만 명인 셀럽이 지속적으로 게시글을 작성하는 상황을 가정하여, 알림 생성에 부하를 유도했습니다.

<br>

✅ **Grafana**

![issue2-2](/docs/img/issue2-2.png)

- 지속적인 게시글 작성으로 알림 생성에 부하가 증가하면서 시스템 CPU 사용률이 100%에 도달했고, 커넥션 풀이 고갈되는 문제가 발생했습니다.
- 이러한 상태에서 알림 조회가 어떻게 동작하는지 확인해 보겠습니다.

<br>

✅ **Postman**

![issue2-3](/docs/img/issue2-3.png)

- 구간별 차이는 있으나, 조회 응답이 약 13초나 걸리는 경우도 확인되었습니다.
- 특히 이때 조회된 데이터 건수는 단 2건에 불과했습니다.

<br>

➡️ 알림 생성과 조회가 하나의 서버에서 처리되므로 CPU, 스레드, 커넥션 등의 자원을 공유하게 되고, 이로 인해 **알림 생성에 부하가 발생하면 알림 조회로도 문제가 전파**됩니다.

<br>

## 3. 알림 생성과 알림 조회의 분리
![issue2-4](/docs/img/issue2-4.png)

- 알림 생성과 알림 조회를 분리한 후, `Nginx`를 리버스 프록시로 사용해 요청 경로에 따른 라우팅을 적용하고 `Redis`를 통해 서버 간 세션 공유 문제를 해결했습니다.

<br>

✅ **알림 생성 서버**

![issue2-5](/docs/img/issue2-5.png)

- 동일한 Test Script로 알림 생성에 부하를 가해, 이전과 비슷하게 자원이 고갈되는 상황을 재현했습니다.
- 생성과 조회를 분리했을 때 알림을 조회해 보겠습니다.

<br>

✅ **알림 조회 서버**

![issue2-6](/docs/img/issue2-6.png)

- 생성 서버의 부하와 상관없이 알림 조회 응답이 즉시 반환되었습니다.
- 서버를 분리한 덕분에 DB 커넥션 여유도 확보되었습니다.

<br>

➡️ 서버를 분리함으로써 더 이상 자원을 공유하지 않고, **각 서버 인스턴스는 고유의 자원을 사용**하게 되었습니다. 그 결과, 알림 생성 서버의 부하가 알림 조회 서버로 전파되는 문제가 발생하지 않습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed/tree/docs-readme#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)
