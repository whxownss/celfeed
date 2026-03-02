package com.xowns.celfeed.exception;

import com.xowns.celfeed.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        ErrorCode errorCode = e.getErrorCode();
        return handleExceptionInternal(errorCode.getStatus(), errorCode.getMessage());
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

        List<Map<String, String>> errorData = new ArrayList<>();
        for (Map.Entry<String, FieldError> entry : errorFieldMap.entrySet()) {
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("field", entry.getKey());
            errorMap.put("message", entry.getValue().getDefaultMessage());

            errorData.add(errorMap);
        }

        return handleExceptionInternal(BAD_REQUEST, "유효한 값을 입력해 주세요.", errorData);
    }

    private int priorityOf(FieldError fieldError) {
        String code = fieldError.getCode();
        if (code == null) return Integer.MAX_VALUE;

        return switch (code) { // 최선인가...
            case "NotBlank" -> 1;
            case "Size", "Email" -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        // MissingServletRequestParameterException 이것도?
        Map<String, Object> errorData = new HashMap<>();
        errorData.put(e.getName(), e.getValue());

        return handleExceptionInternal(BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.", errorData);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("path", request.getRequestURI());
        errorData.put("method", request.getMethod());

        return handleExceptionInternal(BAD_REQUEST, "요청 본문의 형식이 올바르지 않습니다.", errorData);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException e) {
        log.error("handle DataAccessException=", e);

        HttpStatus httpStatus = INTERNAL_SERVER_ERROR;
        String errorMessage = "서버 내부에 오류가 발생하였습니다.";

        if (e instanceof DataIntegrityViolationException divException) {
            httpStatus = CONFLICT;
            errorMessage = "이미 존재하는 리소스입니다.";
        }

        return handleExceptionInternal(httpStatus, errorMessage);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("handle Exception=", e);
        return handleExceptionInternal(INTERNAL_SERVER_ERROR, "알 수 없는 오류가 발생하였습니다.");
    }

    private ResponseEntity<ErrorResponse> handleExceptionInternal(HttpStatus status, String errorMessage) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(errorMessage));
    }

    private ResponseEntity<ErrorResponse> handleExceptionInternal(HttpStatus status,
                                                                  String errorMessage,
                                                                  Object errorData) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(errorMessage, errorData));
    }
}
