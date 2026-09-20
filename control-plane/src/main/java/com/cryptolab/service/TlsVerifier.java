package com.cryptolab.service;

import com.cryptolab.config.LabProperties;
import com.cryptolab.exception.VerificationException;
import com.cryptolab.model.ScenarioDefinition;
import org.springframework.stereotype.Component;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TlsVerifier 是一个实现了 ScenarioVerifier 接口的类，用于验证 TLS/TCP 场景的可达性。
 * 它通过尝试连接到指定的代理主机和端口来验证场景是否可达。
 */
/** 通过 HAProxy 的 TCP 入口验证目标 TLS 路由可达，但不终止 TLS。 */
@Component
public class TlsVerifier implements ScenarioVerifier {
    private final LabProperties properties;

    /** 注入 HAProxy 入口地址。 */
    public TlsVerifier(LabProperties properties) {
        this.properties = properties;
    }

    /** 判断该验证器是否适用于 verification.type=tls 的场景。 */
    public boolean supports(ScenarioDefinition scenario) {
        return "tls".equalsIgnoreCase(scenario.getVerification().getType());
    }

    /** 建立短暂 TCP 连接，确认 HAProxy TLS 入口可达。 */
    public void verify(ScenarioDefinition scenario) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getProxyHost(), properties.getProxyPort()), 2000);
        } catch (Exception e) {
            throw new VerificationException("TLS/TCP entry point is unreachable: " + e.getMessage());
        }
    }
}
