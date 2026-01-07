package com.xowns.celfeed.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ApiAdvice {

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(e.getErrorData(), errorCode.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, FieldError> errorFieldMap = new HashMap<>();

        for (FieldError fieldError : e.getFieldErrors()) {
            String fieldName = fieldError.getField();

            if (!errorFieldMap.containsKey(fieldName)) {
                errorFieldMap.put(fieldName, fieldError);
            } else {
                // 같은 필드에 여러 에러가 있는 경우 우선순위 판단
                if (priorityOf(fieldError) < priorityOf(errorFieldMap.get(fieldName))) {
                    errorFieldMap.put(fieldName, fieldError);
                }
            }
        }

        Map<String, String> errorData = new HashMap<>();
        for (Map.Entry<String, FieldError> entry : errorFieldMap.entrySet()) {
            errorData.put(entry.getKey(), entry.getValue().getDefaultMessage());
        }

        return ResponseEntity
                .status(BAD_REQUEST)
                .body(ErrorResponse.of(errorData, "유효한 값을 입력해 주세요."));
    }

    private int priorityOf(FieldError fieldError) {
        String code = fieldError.getCode();
        if (code == null) return Integer.MAX_VALUE;

        return switch (code) { // 최선인가...
            case "NotBlank" -> 1;
            case "Size" -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("path", request.getRequestURI());
        errorData.put("method", request.getMethod());

        return ResponseEntity
                .status(BAD_REQUEST)
                .body(ErrorResponse.of(errorData, "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException e) {
        log.error("handle DataAccessException=", e);
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(e.getMessage(), "서버 내부에 오류가 발생하였습니다."));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("handle Exception=", e);
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(e.getMessage(), "알 수 없는 오류가 발생하였습니다."));
    }
}
