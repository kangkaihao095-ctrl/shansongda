package com.shansuda.common.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handle(BizException ex) {
        return ResponseEntity.status(ex.httpStatus()).body(ApiResult.fail(ex.code(), ex.getMessage()));
    }
}
