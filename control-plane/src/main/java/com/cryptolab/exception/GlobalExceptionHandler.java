package com.cryptolab.exception;

import com.cryptolab.dto.ApiResponse;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

/** 将领域异常统一转换为带错误码的 REST 响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /** 将无法解析的 JSON 请求转换为明确的 400，而不是内部错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> malformedRequest(HttpMessageNotReadableException e) {
        return ApiResponse.fail("INVALID_REQUEST", "请求 JSON 格式错误");
    }

    /** 将场景参数错误映射为 400，前端可保留弹窗并提示用户修改。 */
    @ExceptionHandler(ConfigurationValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> invalidConfiguration(ConfigurationValidationException e) {
        return ApiResponse.fail("INVALID_SCENARIO_CONFIGURATION", e.getMessage());
    }

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
