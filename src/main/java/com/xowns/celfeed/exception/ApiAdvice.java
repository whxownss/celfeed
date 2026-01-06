package com.xowns.celfeed.exception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ApiAdvice {

    private static final String[] validationOrder = {NotBlank.class.getSimpleName(), Size.class.getSimpleName()};

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(e.getErrorData(), errorCode.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {



        Map<String, List<String>> errorData = e.getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(MessageSourceResolvable::getDefaultMessage, Collectors.toList())
                ));
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(ErrorResponse.of(errorData, "유효한 값을 입력해 주세요."));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        Object errorData = e.getMessage();
        if (e.getCause() instanceof InvalidFormatException ife) {
            errorData = ife.getValue();
        }
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(ErrorResponse.of(errorData, "유효한 값을 입력해 주세요."));
    }


    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("handle Exception=", e);
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(e.getMessage(), "알 수 없는 오류가 발생하였습니다."));
    }
}
