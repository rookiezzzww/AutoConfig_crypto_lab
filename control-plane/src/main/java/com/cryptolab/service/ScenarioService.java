package com.cryptolab.service;

import com.cryptolab.dto.*;
import com.cryptolab.exception.*;
import com.cryptolab.model.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * ScenarioService： 提供了对场景的管理和切换功能。它依赖于 ScenarioRegistry 来
 * 获取场景定义，DockerService 来检查容器状态，ProxyService 来管理 HAProxy 后端，
 * 以及 ValidationService 来验证场景的健康状态。该服务还记录审计日志，以便跟踪场景
 * 切换操作。
 */
/** 管理场景状态机：检查、串行切换、Runtime 验证、审计与失败回滚。 */
@Service
public class ScenarioService {
    private final ScenarioRegistry registry;
    private final DockerService docker;
    private final ProxyService proxy;
    private final ValidationService validation;
    private final AuditService audit;
    private final ReentrantLock switchLock = new ReentrantLock(true);
    private volatile String expectedActiveScenario;
    private volatile boolean switchInProgress;

    /** 注入完成场景切换所需的全部协作服务。 */
    public ScenarioService(ScenarioRegistry registry, DockerService docker, ProxyService proxy,
            ValidationService validation, AuditService audit) {
        this.registry = registry;
        this.docker = docker;
        this.proxy = proxy;
        this.validation = validation;
        this.audit = audit;
    }

    /** 将所有场景定义转换为包含实时状态的响应列表。 */
    public List<ScenarioResponse> list() {
        return registry.findAll().stream().map(this::toResponse).toList();
    }

    /** 获取指定 ID 场景的实时状态。 */
    public ScenarioResponse get(String id) {
        return toResponse(registry.require(id));
    }

    /** 根据 HAProxy Runtime API 查询当前活动场景。 */
    public ScenarioResponse current() {
        String active = proxy.getActiveScenario()
                .orElseThrow(() -> new ProxySwitchException("HAProxy has no active backend"));
        return toResponse(byBackend(active));
    }

    /** 返回控制面最近一次成功切换后的期望场景。 */
    public String expectedActiveScenario() {
        return expectedActiveScenario;
    }

    /** 判断是否正处于受锁保护的切换事务中。 */
    public boolean isSwitchInProgress() {
        return switchInProgress;
    }

    /** 以原子顺序激活目标场景，失败时回滚到此前后端。 */
    public SwitchScenarioResponse activate(String id, String clientIp) {
        long start = System.nanoTime();
        switchLock.lock();
        switchInProgress = true;
        String previous = null;
        try {
            ScenarioDefinition target = registry.require(id);
            if (!target.isEnabled())
                throw new ScenarioUnhealthyException("Scenario is disabled: " + id);
            if (!"prewarmed".equalsIgnoreCase(target.getSwitch().getMode()))
                throw new ScenarioUnhealthyException("Only prewarmed scenarios are supported in this release");
            ContainerInspection inspection = docker.inspectContainer(target.getRuntime().getContainerName());
            if (!inspection.exists() || !inspection.running() || !inspection.healthy())
                throw new ScenarioUnhealthyException("Target scenario is not healthy: " + inspection.detail());
            previous = proxy.getActiveScenario().orElse(null);
            if (target.getRuntime().getBackend().equals(previous)) {
                validation.validateActivation(target);
                return new SwitchScenarioResponse(toScenarioId(previous), id, "Scenario is already active",
                        Instant.now());
            }
            SwitchContext context = new SwitchContext(previous, target.getRuntime().getBackend());
            // Disable all old routes first so the final state never contains two ready
            // backends.
            registry.findAll().forEach(s -> {
                if (!s.getRuntime().getBackend().equals(context.targetBackend()))
                    proxy.disableBackend(s.getRuntime().getBackend());
            });
            proxy.enableBackend(context.targetBackend());
            awaitReady(target);
            validation.validateActivation(target);
            expectedActiveScenario = id;
            long duration = elapsed(start);
            audit.record("SCENARIO_SWITCH", toScenarioId(previous), id, "SUCCESS", "Scenario switched successfully",
                    clientIp, duration);
            return new SwitchScenarioResponse(toScenarioId(previous), id, "Scenario switched successfully",
                    Instant.now());
        } catch (RuntimeException failure) {
            rollback(previous);
            String target = id;
            audit.record("SCENARIO_SWITCH", toScenarioId(previous), target, "FAILED", failure.getMessage(), clientIp,
                    elapsed(start));
            if (failure instanceof ScenarioNotFoundException || failure instanceof ScenarioUnhealthyException
                    || failure instanceof VerificationException)
                throw failure;
            throw new ProxySwitchException("Scenario switch failed and rollback was attempted: " + failure.getMessage(),
                    failure);
        } finally {
            switchInProgress = false;
            switchLock.unlock();
        }
    }

    /** 每 200 毫秒读取一次 Runtime 状态，最多等待十秒。 */
    private void awaitReady(ScenarioDefinition target) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            String state = proxy.getRuntimeStatus().get(target.getRuntime().getBackend());
            if ("UP".equalsIgnoreCase(state) || "OPEN".equalsIgnoreCase(state))
                return;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProxySwitchException("Switch interrupted", e);
            }
        }
        throw new VerificationException("HAProxy backend did not become ready within 10 seconds");
    }

    /** 尽力恢复切换前唯一可用的 HAProxy 后端。 */
    private void rollback(String previousBackend) {
        if (previousBackend == null)
            return;
        try {
            registry.findAll().forEach(s -> {
                if (!s.getRuntime().getBackend().equals(previousBackend))
                    proxy.disableBackend(s.getRuntime().getBackend());
            });
            proxy.enableBackend(previousBackend);
        } catch (RuntimeException ignored) {
            /* original switch error remains primary; audit preserves it */ }
    }

    /** 计算自纳秒起点开始的耗时毫秒数。 */
    private long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** 通过 HAProxy server 名反查场景声明。 */
    private ScenarioDefinition byBackend(String backend) {
        return registry.findAll().stream().filter(s -> s.getRuntime().getBackend().equals(backend)).findFirst()
                .orElseThrow(() -> new ProxySwitchException("Unknown HAProxy backend: " + backend));
    }

    /** 将内部 backend 名转换为公开场景 ID。 */
    private String toScenarioId(String backend) {
        return backend == null ? null : byBackend(backend).getId();
    }

    /** 聚合 Docker、HAProxy 和业务状态，构造前端响应。 */
    private ScenarioResponse toResponse(ScenarioDefinition s) {
        ContainerInspection inspection = docker.inspectContainer(s.getRuntime().getContainerName());
        String haproxy;
        try {
            haproxy = proxy.getRuntimeStatus().getOrDefault(s.getRuntime().getBackend(), "UNKNOWN");
        } catch (RuntimeException e) {
            haproxy = "UNKNOWN";
        }
        ScenarioStatus status = validation.statusOf(s);
        List<VulnerabilityOptionResponse> options = s.getVulnerabilityOptions().stream()
                .map(option -> new VulnerabilityOptionResponse(option.getKey(), option.getName(),
                        option.getDescription(), option.getType(), option.getEnvVar(),
                        option.isSensitive() ? "******" : option.getEffectiveValue(),
                        option.isSensitive() ? "******" : option.getVulnerableValue(),
                        option.isSensitive() ? List.of() : option.getVulnerableValues(),
                        option.isSensitive() ? List.of() : option.getAllowedValues(), option.isSensitive(),
                        option.isRestartRequired(), option.isVulnerabilityConditionMet()))
                .toList();
        boolean conditionsMet = !options.isEmpty()
                && options.stream().allMatch(VulnerabilityOptionResponse::vulnerabilityConditionMet);
        return new ScenarioResponse(s.getId(), s.getName(), s.getCve(), s.getDescription(), s.getCategory(),
                s.getRuntime().getContainerName(), s.getRuntime().getContainerPort(), s.getRuntime().getProtocol(),
                s.isEnabled(), haproxy, status, status == ScenarioStatus.ACTIVE, inspection.health(), options,
                conditionsMet);
    }

    private record SwitchContext(String previousBackend, String targetBackend) {
    }
}
