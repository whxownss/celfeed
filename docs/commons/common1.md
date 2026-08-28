# Spring Interceptor를 활용한 공통 로그인 체크

<br>

## 1. 개요

> 인증이 필요한 API마다 로그인 여부를 확인하는 코드를 직접 작성하면 코드 중복이 발생합니다. 해당 코드를 메서드로 추출할 수도 있지만, 결국 해당 메서드 호출 자체가 반복됩니다.
> 
> 이러한 문제를 Spring MVC의 Interceptor를 활용하여 공통으로 처리했습니다.

<br>

## 2. 왜 Interceptor인가?

> 로그인 체크와 같은 공통 관심사를 Spring의 **AOP**로도 해결할 수 있지만, 웹과 관련된 공통 관심사이므로 Servlet의 **Filter** 또는 Spring의 **Interceptor**를 고려했습니다.
> 
> 현재 프로젝트에서 Spring MVC를 사용하고, Interceptor는 Filter보다 더욱 정밀한 URL 패턴 지정이 가능하기 때문에 **Interceptor**를 선택했습니다.

<br>

## 3. DispatcherServlet

> Interceptor를 적용하기 전에, `DispatcherServlet#doDispatch()`에서 Interceptor가 실행되는 부분을 살펴보겠습니다.

<br>

**✅ DispatcherServlet**

```java
public class DispatcherServlet extends FrameworkServlet {
    ...
    protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    
        HandlerExecutionChain mappedHandler = null;
        ...
        try {
            // 핸들러 조회
            mappedHandler = getHandler(processedRequest);
          
            // 핸들러 어댑터 조회
            HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());
            
            // Inteceptor preHandle() 호출
            if (!mappedHandler.applyPreHandle(processedRequest, response)) {
                return;
            }
            
            // 핸들러 실행
            mv = ha.handle(processedRequest, response, mappedHandler.getHandler());
            
            // Interceptor postHandle() 호출
            mappedHandler.applyPostHandle(processedRequest, response, mv);
            
            // 메서드 내부에서 Interceptor afterCompletion() 호출
            processDispatchResult(..., mappedHandler, ...);
        } catch (Exception ex) {
            // 메서드 내부에서 Interceptor afterCompletion() 호출
            triggerAfterCompletion(..., mappedHandler, ...);
        }
    }
}
```
- Interceptor 기능을 제공하는 `HandlerInterceptor` 인터페이스에는 `preHandle()`, `postHandle()`, `afterCompletion()` 세 가지 메서드가 선언되어 있습니다.
- `preHandle()`은 핸들러 실행 전에 호출되며, false가 반환될 경우 핸들러를 실행하지 않고 return합니다.
- `postHandle()`은 핸들러가 예외 없이 정상적으로 실행된 후에 호출됩니다.
- `afterCompletion()`은 핸들러 정상 실행 후에도, 예외가 발생한 상황에서도 항상 호출됩니다.
- `HandlerInterceptor` 리스트를 필드로 갖는 `HandlerExecutionChain` 내부에서 위 세 가지 메서드가 호출됩니다.

<br>

## 4. Interceptor 적용

> 핸들러가 실행되기 전에 로그인 체크를 해야하므로 `preHandle()` 메서드를 오버라이딩했습니다.

<br>

**✅ LoginCheckInterceptor**
```java
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (request.getRequestURI().equals("/api/members") && HttpMethod.POST.matches(request.getMethod())) {          
            return true; // [1]
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionConst.LOGIN) == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED); // [2]
        }

        return true; // [3]
    }
}
```
**[1]** 로그인이 필요 없는 회원가입은, 회원 목록 조회와 요청 URL(`/api/members`)이 같으므로 Interceptor 내부에서 따로 처리했습니다.

**[2]** 로그인 정보를 저장하는 세션에 아무 정보가 없으면 예외를 발생시킵니다.

**[3]** true를 반환함으로써 다음 Interceptor 또는 핸들러가 실행됩니다.

<br>

**✅ WebConfig**
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
		
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor()) // [1]
                .addPathPatterns("/**") // [2]
                .excludePathPatterns( // [3]
                        "/api/members/validation/nickname",
                        "/api/members/validation/email",
                        "/api/members/login",
                        "/api/members/logout"
                );
    }
}
```
**[1]** `InterceptorRegistry`에 등록된 Interceptor는 `DispatcherServlet#doDispatch()`에서 핸들러를 조회할 때 함께 반환됩니다. 이때 `AbstractHandlerMapping`이 요청 URL과 매치되는 Interceptor만 추가합니다.

**[2]** 모든 경로에 대해 로그인 체크 Interceptor를 적용합니다.

**[3]** 닉네임 중복 검사, 이메일 중복 검사, 로그인, 로그아웃에 대해서는 로그인 체크를 하지 않습니다.

<br>

➡️ Spring MVC의 **Interceptor**를 통해 **인증 로직을 한 곳에서 관리**할 수 있게 되었고, **비즈니스 로직은 핵심 기능 구현에만 집중**할 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed)
