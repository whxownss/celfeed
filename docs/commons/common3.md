# @ExceptionHandler를 활용한 공통 예외 처리 (ft. HandlerExceptionResolver)

<br>

## 1. 개요
 
> 애플리케이션을 개발하면서 예외 처리는 필수적인 요소입니다. 하지만 비즈니스 로직에 직접 `try-catch` 문을 작성하면 코드가 중복되고 가독성 및 유지보수성이 떨어지는 문제가 있습니다.
> 
> Spring의 `@ExceptionHandler`와 `@RestControllerAdvice`를 활용하여 위의 문제를 해결했습니다.

<br>

## 2. HandlerExceptionResolver
 
> Spring MVC는 `HandlerExceptionResolver`를 통해 핸들러 밖으로 던져진 예외를 처리하고, 예외 발생 시의 동작을 새로 정의할 수 있도록 지원합니다.
> 
> `@ExceptionHandler` 동작은 `ExceptionHandlerExceptionResolver`에 의해 실행됩니다.

<br>

**✅ DispatcherServlet**
```java
public class DispatcherServlet extends FrameworkServlet {
    private List<HandlerExceptionResolver> handlerExceptionResolvers;
    ...
    protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        ...
        Exception dispatchException = null;
        ...
        try {
            // 핸들러 조회, 핸들러 어댑터 조회, 핸들러 실행, ...
        } catch (Exception ex) {
            dispatchException  = ex;
        }
        // 내부에서 processHandlerException() 호출
        processDispatchResult(..., dispatchException);
    }
    
    protected ModelAndView processHandlerException(HttpServletRequest request,
                                                   HttpServletResponse response,
                                                   Object handler,
                                                   Exception ex) throws Exception {
            ...
            ModelAndView exMv = null;
            if (this.handlerExceptionResolvers != null) {
                for (HandlerExceptionResolver resolver : this.handlerExceptionResolvers) {
                    // 예외 처리
                    exMv = resolver.resolveException(request, response, handler, ex);
                    if (exMv != null) break;
                }
            }
            ...
            return exMv;
    }																								   
}
```
- 루프를 돌며 예외 처리를 하는 `this.handlerExceptionResolvers`는 아래와 같습니다.
    
    ![common3-1](/docs/img/common3-1.png)
    
    - 첫 번째 요소인 `DefaultErrorAttributes`에서는 의도적으로 null을 반환합니다.
    - `HandlerExceptionResolverComposite`에서 실질적인 예외 처리를 합니다.
    - `ExceptionHandlerExceptionResolver`는 발생한 예외에 대한 `@ExceptionHandler` 메서드를 찾아 실행합니다.

<br>

## 3. ApiException

> `@ExceptionHandler`를 적용하기 전에, 비즈니스 로직에 맞게 직접 정의한 예외를 살펴보겠습니다.

<br>

**✅ ApiException**
```java
@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```
- `ErrorCode`를 필드로 갖는 런타임 예외입니다.

<br>

**✅ ErrorCode**
```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 등록된 닉네임입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 등록된 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    LOGIN_FAIL(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ...
    ;

    private final HttpStatus status;
    private final String message;
}
```
- 예외가 발생했을 때 클라이언트에 반환할 HTTP 상태 코드와 에러 메시지를 정의합니다.
- 필요한 곳에 `throw new ApiException(ErrorCode.LOGIN_FAIL);` 와 같이 사용하면 됩니다.

<br>

## 4. @ExceptionHandler 적용

> 예외 처리 코드를 컨트롤러로부터 따로 분리하기 위해 `@RestControllerAdvice`를 사용했습니다.

<br>

**✅ GlobalExceptionHandler**
```java
@Slf4j
@RestControllerAdvice(annotations = RestController.class) // [1]
public class GlobalExceptionHandler {
		
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(ApiException e) { // [2]
        log.error("handle ApiException", e);
        ErrorCode errorCode = e.getErrorCode();
        return handleExceptionInternal(errorCode.getStatus(), errorCode.getMessage());
    }
    
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(DataAccessException e) { // [3]
        log.error("handle DataAccessException", e);
        String errorMessage = "서버 내부에 오류가 발생하였습니다.";
        return handleExceptionInternal(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage);
    }
    
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(Exception e) { // [4]
        log.error("handle Exception", e);
        String errorMessage = "알 수 없는 오류가 발생하였습니다.";
        return handleExceptionInternal(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage);
    }

    private ResponseEntity<ErrorResponse> handleExceptionInternal(HttpStatus status, String errorMessage) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(errorMessage)); // [5]
    }
}
```
**[1]** `@RestController`가 붙은 컨트롤러를 대상으로 지정했습니다.

**[2]** 직접 정의한 `ApiException`에 대한 예외 처리를 합니다.

**[3]** 직접 정의한 예외뿐만 아니라 Spring이 추상화한 예외에 대해서도 처리가 가능합니다.

**[4]** 지정하지 않은 모든 예외를 처리합니다.

**[5]** 예외에 대한 공통 응답 포맷입니다.

<br>

➡️ Spring이 제공하는 `@ExceptionHandler`와 `@RestControllerAdvice`를 활용하여 **예외 처리 로직을 한 곳으로 모으고, 일관된 에러 응답**을 제공할 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#5-%EA%B3%B5%ED%86%B5-%EC%B2%98%EB%A6%AC)
