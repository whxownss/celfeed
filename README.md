# CelFeed

## 1. 소개
**CelFeed**(Celeb + Feed)는 셀럽 전용 SNS를 가정해 개발한 백엔드 API 프로젝트입니다.

셀럽이 게시글을 작성하면 팬들에게 알림이 전송되고, 팬이 게시글에 좋아요를 누르면 셀럽에게 알림이 전달됩니다.
이처럼 알림 이벤트를 중심으로 알림 기능 구현에 중점을 두었습니다.

<br>

## 2. 사용 기술 & 아키텍처
<img width="600" height="325" alt="damn2 drawio" src="docs/img/skills.png" /> 사용 기술
<br><br>

<img width="600" height="350" alt="아키텍처" src="docs/img/architecture.png" /> 아키텍처
- 사용자의 요청은 Nginx에서 경로에 따라 **기본 서버** 또는 **알림 조회 서버**로 라우팅됩니다.
- **알림 생성 서버**는 외부에 노출되지 않고, 기본 서버(Producer)에서 발생한 이벤트가 Kafka로 전달되면 알림 생성 서버(Consumer)가 이벤트를 처리합니다.
- **기본 DB**에는 회원, 게시글 등의 테이블이 존재하며, **알림 DB**에는 샤딩을 적용했습니다.

<br>

## 3. ERD
<img width="656" height="297" alt="c" src="https://github.com/user-attachments/assets/5b33f3ed-f495-4487-8768-61d4dc99b55b" />

<br><br>

## 4. 프로젝트 상세
[프로젝트 상세](https://www.notion.so/306786a849b480c9b8f6c02880979369?source=copy_link#306786a849b480429098d68ccc147108)
