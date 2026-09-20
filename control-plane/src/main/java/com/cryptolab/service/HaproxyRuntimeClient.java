package com.cryptolab.service;


/**
 * HaproxyRuntimeClient 是一个接口，定义了与 HAProxy 运行时交互的功能。
 * 它提供了一个方法 execute，用于执行指定的命令并返回结果。
 */
/** 抽象 HAProxy Runtime API 通信，便于替换实现和单元测试。 */
public interface HaproxyRuntimeClient {
    /** 执行一条 Runtime 命令并返回原始文本响应。 */
    String execute(String command);
}
