package com.cryptolab.service;

import com.cryptolab.exception.VerificationException;
import com.cryptolab.model.*;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 
 * ValidationService： 提供了对场景的验证功能。它依赖于 DockerService 来检查容器状态，
 * ProxyService 来获取 HAProxy 的运行时状态，以及 ScenarioVerifier 列表来验证场景的可达性。
 * 该服务提供了获取场景状态和验证场景激活的功能。
 */
/** 交叉验证容器健康、HAProxy 后端唯一性和 TCP 入口可达性。 */
@Service
public class ValidationService {
    private final DockerService docker;
    private final ProxyService proxy;
    private final List<ScenarioVerifier> verifiers;

    /** 注入容器检查、代理状态和可扩展验证器。 */
    public ValidationService(DockerService docker, ProxyService proxy, List<ScenarioVerifier> verifiers) {
        this.docker = docker;
        this.proxy = proxy;
        this.verifiers = verifiers;
    }

    /**
     * 获取指定场景的状态。容器不存在或未运行时返回 STOPPED，存在但不健康时返回 UNHEALTHY。
     * 然后，它获取 HAProxy 的运行时状态，并根据后端的状态返回相应的 ScenarioStatus。如果后端处于维护状态，则返回 STANDBY；
     * 如果后端处于其他状态，则返回 UNKNOWN。最后，如果后端处于 UP 或 OPEN 状态，它会确保该场景是唯一的活动后端，并通过代理验证场景的可达性。
     * @param scenario
     * @return
     */
    /** 计算场景的 STOPPED、ACTIVE、STANDBY、UNHEALTHY 或 UNKNOWN 状态。 */
    public ScenarioStatus statusOf(ScenarioDefinition scenario) {
        ContainerInspection container = docker.inspectContainer(scenario.getRuntime().getContainerName());
        if (!container.exists() || !container.running())
            return ScenarioStatus.STOPPED;
        if (!container.healthy())
            return ScenarioStatus.UNHEALTHY;
        Map<String, String> runtime;
        try {
            runtime = proxy.getRuntimeStatus();
        } catch (RuntimeException e) {
            return ScenarioStatus.UNKNOWN;
        }
        String state;
        state = runtime.get(scenario.getRuntime().getBackend());
        if ("MAINT".equalsIgnoreCase(state))
            return ScenarioStatus.STANDBY;
        if ("UP".equalsIgnoreCase(state) || "OPEN".equalsIgnoreCase(state)) {
            try {
                ensureTargetIsSoleActiveBackend(scenario, runtime);
                verifyThroughProxy(scenario);
                return ScenarioStatus.ACTIVE;
            } catch (VerificationException e) {
                return ScenarioStatus.UNHEALTHY;
            }
        }
        return ScenarioStatus.UNKNOWN;
    }

    /**
     * 验证指定场景的激活状态。它首先检查容器的状态，如果容器不存在、未运行或不健康，则抛出 VerificationException。
     * 然后，它获取 HAProxy 的运行时状态，并检查指定场景的后端是否处于 UP 或 OPEN 状态。如果后端不处于这些状态，
     * 则抛出 VerificationException。接下来，它确保该场景是唯一的活动后端，并通过代理验证场景的可达性。
     * 如果任何验证失败，它将抛出 VerificationException。
     * @param scenario
     */
    /** 在提交切换前确认目标是唯一活跃、健康且可经 HAProxy 访问的后端。 */
    public void validateActivation(ScenarioDefinition scenario) {
        ContainerInspection container = docker.inspectContainer(scenario.getRuntime().getContainerName());
        if (!container.exists() || !container.running() || !container.healthy())
            throw new VerificationException("Target container is not healthy: " + container.detail());
        Map<String, String> runtime = proxy.getRuntimeStatus();
        String state = runtime.get(scenario.getRuntime().getBackend());
        if (!("UP".equalsIgnoreCase(state) || "OPEN".equalsIgnoreCase(state)))
            throw new VerificationException("HAProxy backend is not ready: " + state);
        ensureTargetIsSoleActiveBackend(scenario, runtime);
        verifyThroughProxy(scenario);
    }

    /** 确保 Runtime API 中恰有一个 ready 后端，且它就是目标场景。 */
    private void ensureTargetIsSoleActiveBackend(ScenarioDefinition scenario, Map<String, String> runtime) {
        List<String> active = runtime.entrySet().stream()
                .filter(e -> isReadyState(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (active.size() != 1) {
            throw new VerificationException(
                    "Expected exactly one active backend but found " + active.size() + ": " + active);
        }
        if (!scenario.getRuntime().getBackend().equals(active.get(0))) {
            throw new VerificationException("HAProxy active backend mismatch. expected="
                    + scenario.getRuntime().getBackend() + ", actual=" + active.get(0));
        }
    }

    /** 判断 HAProxy 状态是否代表可接受连接。 */
    private boolean isReadyState(String value) {
        return "UP".equalsIgnoreCase(value) || "OPEN".equalsIgnoreCase(value);
    }

    /** 按验证类型选择匹配的验证器并执行验证。 */
    private void verifyThroughProxy(ScenarioDefinition scenario) {
        verifiers.stream().filter(v -> v.supports(scenario)).findFirst()
                .orElseThrow(() -> new VerificationException("No verifier for " + scenario.getVerification().getType()))
                .verify(scenario);
    }
}
