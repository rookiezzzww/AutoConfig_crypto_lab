package com.cryptolab.exception;

/** 表示用户提交的漏洞初始化参数不在场景声明的合法范围内。 */
public class ConfigurationValidationException extends RuntimeException {
    /** 使用适合直接反馈给前端的校验说明创建异常。 */
    public ConfigurationValidationException(String message) {
        super(message);
    }
}
