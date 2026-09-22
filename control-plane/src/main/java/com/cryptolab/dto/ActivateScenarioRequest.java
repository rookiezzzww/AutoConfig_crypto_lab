package com.cryptolab.dto;

import java.util.Map;

/** 前端提交的场景初始化参数及旧场景回收选择。 */
public record ActivateScenarioRequest(Map<String, String> options, boolean stopPrevious) {
    /** 将空参数对象规范化为空映射，避免业务层处理 null。 */
    public Map<String, String> safeOptions() {
        return options == null ? Map.of() : options;
    }
}
