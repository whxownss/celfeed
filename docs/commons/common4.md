# 공통 응답 포맷

<br>

## 1. 개요

> 각 API가 정해진 규칙 없이 다양한 형태로 데이터를 응답할 경우, 클라이언트 개발 복잡도가 증가하고 API 변경 시 그 영향을 예측하기 어려워지는 등의 문제가 있습니다.
> 
> 이러한 문제를 해결하기 위해 모든 API에 공통된 규칙을 적용했습니다.

<br>

## 2. 공통 응답 객체

> 응답은 그 결과에 따라 성공과 실패 두 가지로 구분됩니다.

<br>

**✅ BaseResponse**
```java
@Getter
public abstract class BaseResponse {
    private boolean success;
    private String message;

    protected BaseResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
```
- 정상(성공)과 실패 응답 객체의 공통 부모 클래스입니다.
- 성공 여부(`success`)와 메시지(`message`)를 필드로 갖습니다.

<br>

**✅ ApiResponse**
```java
@Getter
public class ApiResponse<T> extends BaseResponse { // [1]

    @JsonInclude(JsonInclude.Include.NON_NULL) // [2]
    private T data;

    private ApiResponse(String message, T data) {
        super(true, message); // **[3]**
        this.data = data;
    }

    public static ApiResponse<Void> of(String message) { // [4]
        return of(message, null);
    }

    public static <D> ApiResponse<D> of(String message, D data) { // [5]
        return new ApiResponse<>(message, data);
    }
}
```
**[1]** 정상 응답 객체입니다.

**[2]** 직렬화 과정에서 `data`가 null이면 JSON에 포함시키지 않습니다.

**[3]** 부모 객체에 메시지를 저장하고, 성공 여부(`success`)는 true로 설정합니다.

**[4]** 데이터 없이 메시지만 반환할 때 사용합니다.

**[5]** 메시지와 데이터를 함께 반환할 때 사용합니다.

<br>

**✅ ErrorResponse**
```java
@Getter
public class ErrorResponse extends BaseResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object errorData;

    private ErrorResponse(String errorMessage, Object errorData) {
        super(false, errorMessage);
        this.errorData = errorData;
    }

    public static ErrorResponse of(String errorMessage) {
        return of(errorMessage, null);
    }

    public static ErrorResponse of(String errorMessage, Object errorData) {
        return new ErrorResponse(errorMessage, errorData);
    }
}
```
- 실패 응답 객체이며, 성공 여부는 false로 설정합니다.

<br>

## 3. ResponseEntity와 함께 사용

> 응답 시 HTTP 상태 코드를 제어하기 위해, 공통 응답 객체와 `ResponseEntity`를 함께 사용했습니다.

<br>

### ✏️ 정상 응답

<br>

**✅ ResponseEntityUtils**
```java
public abstract class ResponseEntityUtils {

    public static ResponseEntity<ApiResponse<Void>> ok(String message) {
        return ResponseEntity.ok(ApiResponse.of(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.of(message, data));
    }

    public static ResponseEntity<ApiResponse<Void>> create(String message) {
        return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.of(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> create(String message, T data) {
        return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.of(message, data));
    }
}
```
- 컨트롤러에서 정상 응답 시 호출하는 메서드를 모아 둔 유틸 클래스로, 공통 응답 형식을 편리하게 생성해 줍니다.
- `ok()`는 HTTP 상태 코드 200으로 응답하며, 데이터 포함 여부에 따라 적절한 메서드를 제공합니다.
- `create()`는 HTTP 상태 코드 201로 응답하며, 데이터 포함 여부에 따라 적절한 메서드를 제공합니다.

<br>

**✅ MemberController**
```java
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController { // [1]
    private final MemberService memberService;
    
    @GetMapping("/validation/email")
    public ResponseEntity<ApiResponse<Void>> validateEmail(@RequestParam String email) {
        memberService.validateDuplicateNickname(email);
        return ResponseEntityUtils.ok("사용 가능한 이메일입니다."); // [2]
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createMember(@Valid @RequestBody MemberRequest memberRequest) {
        Long memberId = memberService.join(memberRequest);
        return ResponseEntityUtils.create("성공적으로 가입되었습니다.", memberId); // [3]
    }
    ...
}
```
**[1]** `ResponseEntityUtils`의 메서드를 호출하는 컨트롤러 코드입니다.

**[2]** 데이터 없이 메시지만 반환하며, 200 코드로 응답합니다.

**[3]** 메시지와 함께 데이터도 반환하며, 201 코드로 응답합니다.

<br>

**✅ 정상 응답 예시**

![common4-1](/docs/img/common4-1.png) ![common4-2](/docs/img/common4-2.png)

<br>

### ✏️ 실패 응답

<br>

**✅ GlobalExceptionHandler**
```java
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler { // [1]

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(ApiException e) {
        ErrorCode errorCode = e.getErrorCode();
        return handleExceptionInternal(errorCode.getStatus(), errorCode.getMessage());
    }
    ...
    private ResponseEntity<ErrorResponse> handleExceptionInternal(HttpStatus status, String errorMessage) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(errorMessage)); // [2]
    }

    private ResponseEntity<ErrorResponse> handleExceptionInternal(HttpStatus status, String errorMessage, Object errorData) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(errorMessage, errorData)); // [3]
    }
}
```
**[1]** 공통 예외 처리를 하는 클래스로, 예외 발생 시 실패 응답 객체(`ErrorResponse`)를 생성하여 `ResponseEntity`와 함께 반환합니다.

**[2]** 데이터 없이 메시지만 반환하며, 지정한 상태 코드로 응답합니다.

**[3]** 메시지와 함께 데이터도 반환하며, 지정한 상태 코드로 응답합니다.

<br>

**✅ 실패 응답 예시**

![common4-3](/docs/img/common4-3.png) ![common4-4](/docs/img/common4-4.png)

<br>

➡️ **공통 응답 포맷**을 정의하고 적용함으로써, API 응답 구조를 **일관되고 예측 가능**하게 정리할 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#5-%EA%B3%B5%ED%86%B5-%EC%B2%98%EB%A6%AC)
