package com.xowns.celfeed.common.web.exception;

import com.xowns.celfeed.common.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검증 에러 우선순위 로직 단위 테스트
 * (common-web 에는 Bean Validation 구현체가 없어서 MockMvc 대신 예외를 직접 만들어 검증)
 * 나머지 예외 매핑은 WebConfigMvcTest 에서 실제 요청으로 검증한다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void handlerMethod(Object request) {
    }

    @Test
    @DisplayName("필드 에러는 필드당 하나만, 메시지와 함께 400 으로 응답한다")
    void oneErrorPerField() throws Exception {
        MethodArgumentNotValidException e = notValid(
                fieldError("email", "Email", "이메일 형식이 아닙니다."),
                fieldError("nickname", "Size", "닉네임은 2~20자입니다.")
        );

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValidException(e);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getErrorMessage()).isEqualTo("유효한 값을 입력해 주세요.");
        assertThat(errorData(response)).containsExactlyInAnyOrder(
                Map.of("field", "email", "message", "이메일 형식이 아닙니다."),
                Map.of("field", "nickname", "message", "닉네임은 2~20자입니다.")
        );
    }

    @Test
    @DisplayName("같은 필드에 에러가 여러 개면 NotBlank 를 Size/Email 보다 우선한다 (순서 무관)")
    void notBlankFirst() throws Exception {
        MethodArgumentNotValidException sizeFirst = notValid(
                fieldError("nickname", "Size", "닉네임은 2~20자입니다."),
                fieldError("nickname", "NotBlank", "닉네임을 입력해 주세요.")
        );
        MethodArgumentNotValidException notBlankFirst = notValid(
                fieldError("nickname", "NotBlank", "닉네임을 입력해 주세요."),
                fieldError("nickname", "Size", "닉네임은 2~20자입니다.")
        );

        assertThat(errorData(handler.handleMethodArgumentNotValidException(sizeFirst)))
                .containsExactly(Map.of("field", "nickname", "message", "닉네임을 입력해 주세요."));
        assertThat(errorData(handler.handleMethodArgumentNotValidException(notBlankFirst)))
                .containsExactly(Map.of("field", "nickname", "message", "닉네임을 입력해 주세요."));
    }

    @Test
    @DisplayName("우선순위가 정의되지 않은 에러보다 Size/Email 을 우선한다")
    void sizeBeforeUnknown() throws Exception {
        MethodArgumentNotValidException e = notValid(
                fieldError("email", "Pattern", "허용되지 않는 문자가 있습니다."),
                fieldError("email", "Email", "이메일 형식이 아닙니다.")
        );

        assertThat(errorData(handler.handleMethodArgumentNotValidException(e)))
                .containsExactly(Map.of("field", "email", "message", "이메일 형식이 아닙니다."));
    }

    @Test
    @DisplayName("우선순위가 같으면 먼저 발생한 에러를 유지한다")
    void samePriorityKeepsFirst() throws Exception {
        MethodArgumentNotValidException e = notValid(
                fieldError("email", "Size", "이메일은 100자 이하입니다."),
                fieldError("email", "Email", "이메일 형식이 아닙니다.")
        );

        assertThat(errorData(handler.handleMethodArgumentNotValidException(e)))
                .containsExactly(Map.of("field", "email", "message", "이메일은 100자 이하입니다."));
    }

    // ========================================================================================

    private MethodArgumentNotValidException notValid(FieldError... errors) throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        for (FieldError error : errors) {
            bindingResult.addError(error);
        }

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("handlerMethod", Object.class), 0);

        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    /** 실제 검증기는 code 배열 마지막에 "NotBlank" 같은 어노테이션 이름을 넣는다 (FieldError#getCode 가 마지막 값을 반환) */
    private static FieldError fieldError(String field, String code, String message) {
        return new FieldError("request", field, null, false,
                new String[]{code + ".request." + field, code}, null, message);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> errorData(ResponseEntity<ErrorResponse> response) {
        return (List<Map<String, String>>) response.getBody().getErrorData();
    }
}
