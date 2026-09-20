package com.cryptolab.exception;

import com.cryptolab.dto.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 将领域异常统一转换为带错误码的 REST 响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ScenarioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiResponse<Void> notFound(ScenarioNotFoundException e) {
        return ApiResponse.fail("SCENARIO_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ScenarioUnhealthyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiResponse<Void> unhealthy(ScenarioUnhealthyException e) {
        return ApiResponse.fail("SCENARIO_UNHEALTHY", e.getMessage());
    }

    @ExceptionHandler(VerificationException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiResponse<Void> verify(VerificationException e) {
        return ApiResponse.fail("VERIFICATION_FAILED", e.getMessage());
    }

    @ExceptionHandler(ProxySwitchException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ApiResponse<Void> proxy(ProxySwitchException e) {
        return ApiResponse.fail("PROXY_SWITCH_FAILED", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiResponse<Void> other(Exception e) {
        return ApiResponse.fail("INTERNAL_ERROR", e.getMessage());
    }
}
