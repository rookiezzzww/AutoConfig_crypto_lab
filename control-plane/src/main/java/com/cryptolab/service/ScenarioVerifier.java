package com.cryptolab.service;

import com.cryptolab.model.ScenarioDefinition;

/**
 * 
 * ScenarioVerifier 是一个接口，定义了验证场景定义的功能。它提供了两个方法：
 * supports 用于检查是否支持特定的场景定义，verify 用于执行验证逻辑。
 */
/** 定义可按场景 verification.type 扩展的验证器契约。 */
public interface ScenarioVerifier {
    /** 判断当前验证器是否支持该场景。 */
    boolean supports(ScenarioDefinition scenario);

    /** 执行验证，失败时抛出 VerificationException。 */
    void verify(ScenarioDefinition scenario);
}
