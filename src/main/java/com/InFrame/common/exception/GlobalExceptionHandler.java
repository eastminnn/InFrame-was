package com.InFrame.common.exception;

import com.InFrame.common.exception.error.ErrorCode;
import com.InFrame.common.exception.error.ErrorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 커스텀 예외처리
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<?> customExceptionHandler(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        ErrorDto errorDto = new ErrorDto(errorCode.getStatus(), errorCode.getMessage());
        return new ResponseEntity<>(errorDto, HttpStatusCode.valueOf(errorCode.getStatus()));
    }

    // 파라미터 타입 변환 실패, 읽을 수 없는 요청 본문(깨진 JSON 등)
    @ExceptionHandler({TypeMismatchException.class, HttpMessageNotReadableException.class})
    protected ResponseEntity<?> badRequestHandler(Exception e) {
        log.warn("BAD_REQUEST: {}", e.getMessage());
        ErrorDto errorDto = new ErrorDto(ErrorCode.BAD_REQUEST.getStatus(), ErrorCode.BAD_REQUEST.getMessage());
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    // 락 대기 시간 초과, 데드락(재시도까지 실패). 다시 시도하면 성공할 수 있으므로 500 이 아닌 503
    @ExceptionHandler(PessimisticLockingFailureException.class)
    protected ResponseEntity<?> lockFailureHandler(PessimisticLockingFailureException e) {
        log.warn("LOCK_WAIT_TIMEOUT: {}", e.getMostSpecificCause().getMessage());
        ErrorDto errorDto = new ErrorDto(ErrorCode.LOCK_WAIT_TIMEOUT.getStatus(), ErrorCode.LOCK_WAIT_TIMEOUT.getMessage());
        return new ResponseEntity<>(errorDto, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // 일반 예외처리
    @ExceptionHandler
    protected ResponseEntity<?> customServerException(Exception e){
        // Spring MVC 예외(없는 경로, 메서드 불일치, 파라미터 누락 등)는 자체 상태 코드를 가진다
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return clientErrorResponse(errorResponse, e);
        }

        log.error("INTERNAL_SERVER_ERROR", e);
        ErrorDto errorDto = new ErrorDto(ErrorCode.INTERNAL_SERVER_ERROR.getStatus(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<?> clientErrorResponse(ErrorResponse errorResponse, Exception e) {
        int status = errorResponse.getStatusCode().value();
        log.warn("CLIENT_ERROR {}: {}", status, e.getMessage());
        ErrorCode errorCode = switch (status) {
            case 404 -> ErrorCode.PATH_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.BAD_REQUEST;
        };
        // 405 의 Allow 처럼 스프링이 붙여둔 헤더는 그대로 전달
        return new ResponseEntity<>(new ErrorDto(status, errorCode.getMessage()), errorResponse.getHeaders(), status);
    }

    // 메소드 인자 타당성 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        for(FieldError error : e.getBindingResult().getFieldErrors())
            errors.put(error.getField(), error.getDefaultMessage());
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}
