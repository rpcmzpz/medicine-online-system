package com.medicine.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        Result result = Result.fail(e.getMessage(), e.getCode());
        return ResponseEntity.status(e.getHttpStatus()).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(e.getMessage(), 40002));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception e) {
        log.error("系统异常", e);
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        return ResponseEntity.status(500).body(Result.fail("系统内部错误: " + msg, 50000));
    }
}
