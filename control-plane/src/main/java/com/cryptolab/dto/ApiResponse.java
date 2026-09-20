package com.cryptolab.dto;
import java.time.Instant;
/** 统一 REST 返回结构：成功时携带数据，失败时携带错误码与说明。 */
public record ApiResponse<T>(boolean success, T data, String code, String message, Instant timestamp) {
    /** 构造带当前时间戳的成功响应。 */
    public static <T> ApiResponse<T> ok(T data) { 
        return new ApiResponse<>(true, data, null, null, Instant.now()); 
    }
    /** 构造带当前时间戳的失败响应。 */
    public static <T> ApiResponse<T> fail(String code, String message) { 
        return new ApiResponse<>(false, null, code, message, Instant.now()); 
    }
}
