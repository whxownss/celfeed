# HandlerMethodArgumentResolver를 활용하여 로그인 ID 쉽게 가져오기

<br>

## 1. 개요
 
> 비즈니스 로직을 수행하면서 로그인한 회원의 ID가 필요한 경우가 빈번하게 있습니다. 기존에는 필요할 때마다 세션에서 ID를 직접 가져왔지만, 이렇게 하면 코드가 중복되는 문제가 발생합니다.
> 
> 이러한 문제를 `HandlerMethodArgumentResolver`를 활용하여 해결했습니다.

<br>

## 2. HandlerMethodArgumentResolver

> Spring MVC는 `HandlerMethodArgumentResolver`를 통해 핸들러 메서드의 파라미터를 어떻게 채워줄지 정의할 수 있도록 지원합니다.

<br>

✅ **HandlerMethodArgumentResolver**
```java
public interface HandlerMethodArgumentResolver { // [1]
    boolean supportsParameter(MethodParameter parameter); // [2]
    
    Object resolveArgument(MethodParameter parameter, // [3]
                           ModelAndViewContainer mavContainer,
                           NativeWebRequest webRequest,
                           WebDataBinderFactory binderFactory) throws Exception;
}
```
**[1]** `HandlerMethodArgumentResolver` 인터페이스는 두 가지 메서드를 제공합니다.

**[2]** 파라미터로 주어진 `MethodParameter`가 이 리졸버에서 지원되는지 여부를 확인합니다.

**[3]** `supportsParameter()`에서 true를 반환한 경우, 요청 데이터에서 값을 꺼내어 파라미터 타입에 맞게 변환하고 반환합니다.

<br>

**✅ RequestMappingHandlerAdapter**
```java
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter implements ... { // [1]
    private HandlerMethodArgumentResolverComposite argumentResolvers;
    ...	
    protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
                                               HttpServletResponse response,
                                               HandlerMethod handlerMethod) 
                                               throws Exception {
        ...
        ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
                        
        if (this.argumentResolvers != null) { // [2]
            invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
        }
        ...
        invocableMethod.invokeAndHandle(webRequest, mavContainer); // [3]
        ...
    }
}
```
**[1]** `RequestMappingHandlerAdapter`는 컨트롤러에 `@RequestMapping`으로 정의된 핸들러 메서드를 실행하는 역할을 합니다.

**[2]** `HandlerMethodArgumentResolver` 리스트가 있는 `this.argumentResolvers`를 실행할 핸들러 메서드에 등록합니다.

![common2-1](/docs/img/common2-1.png)
    

**[3]** 핸들러 메서드를 실행하는 코드로, 내부적으로는 메서드의 각 파라미터에 대해 등록된 리졸버 리스트를 확인하고, 지원 가능한 리졸버가 있으면 해당 리졸버로 값을 변환한 뒤 주입하는 과정이 포함됩니다.

<br>

## 3. 리졸버 사용

> `HandlerMethodArgumentResolver`를 구현하여 로그인한 ID를 파라미터로 쉽게 가져와 보겠습니다.

<br>

**✅ @Login**
```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Login {
}
```
- `supportsParameter()`에서 해당 애노테이션이 메서드 파라미터에 있는지 확인합니다.

<br>

**✅ LoginMemberArgumentResolver**
```java
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {
		
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasLoginAnnotation = parameter.hasParameterAnnotation(Login.class);
        boolean hasLongType = Long.class.isAssignableFrom(parameter.getParameterType());
    
        return hasLoginAnnotation && hasLongType; // [1]
    }

    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter,
                                            ModelAndViewContainer mavContainer,
                                            NativeWebRequest webRequest,
                                            WebDataBinderFactory binderFactory) throws Exception {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        HttpSession session = request.getSession(false);

        if (session == null) return null; // [2]

        return session.getAttribute(SessionConst.LOGIN_MEMBER); // [3]
    }
}
```
**[1]** 메서드의 파라미터에 `@Login` 애노테이션이 있고, Long 타입인 경우에만 true를 반환합니다.

**[2]** 세션이 null인(로그인을 하지 않은) 경우 null을 반환하지만 로그인을 하지 않았을 때는 Interceptor에 의해 핸들러 메서드가 실행되지 않습니다.

**[3]** 세션에서 로그인 ID를 가져와 반환합니다. 

<br>

**✅ PostController**
```java
@DeleteMapping("/{postId}")
public ResponseEntity<ApiResponse<Void>> deletePost(@Login Long loginId, // **[1]**
													@PathVariable Long postId) {
    postService.deletePost(loginId, postId);
    return ResponseEntityUtils.ok("게시글이 삭제되었습니다.");
}
```
**[1]** `@Login` 애노테이션만 붙여주면 손쉽게 로그인 ID를 가져올 수 있습니다.

<br>

➡️ `HandlerMethodArgumentResolver`를 통해 세션에서 로그인 ID를 가져오는 반복 코드를 없앨 수 있었고, 애노테이션까지 활용하여 핸들러 메서드 파리미터에 로그인 ID를 편리하게 주입받을 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#5-%EA%B3%B5%ED%86%B5-%EC%B2%98%EB%A6%AC)
