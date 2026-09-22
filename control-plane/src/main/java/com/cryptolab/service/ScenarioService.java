package com.cryptolab.service;

import com.cryptolab.dto.ScenarioResponse;
import com.cryptolab.dto.SwitchScenarioResponse;
import com.cryptolab.dto.VulnerabilityOptionResponse;
import com.cryptolab.exception.ProxySwitchException;
import com.cryptolab.exception.ScenarioNotFoundException;
import com.cryptolab.exception.ScenarioUnhealthyException;
import com.cryptolab.exception.VerificationException;
import com.cryptolab.model.ContainerInspection;
import com.cryptolab.model.ScenarioDefinition;
import com.cryptolab.model.ScenarioStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

/** 管理按需创建、参数化初始化、Runtime API 切流、回滚和旧容器回收。 */
@Service
public class ScenarioService {
    private final ScenarioRegistry registry;
    private final DockerService docker;
    private final ProxyService proxy;
    private final ValidationService validation;
    private final AuditService audit;
    private final ReentrantLock switchLock = new ReentrantLock(true);
    private final Set<String> initializingScenarios = ConcurrentHashMap.newKeySet();
    private volatile String expectedActiveScenario;
    private volatile boolean switchInProgress;

    /** 注入场景切换所需的注册表、Docker、代理、验证和审计服务。 */
    public ScenarioService(ScenarioRegistry registry, DockerService docker, ProxyService proxy,
            ValidationService validation, AuditService audit) {
        this.registry = registry;
        this.docker = docker;
        this.proxy = proxy;
        this.validation = validation;
        this.audit = audit;
    }

    /** 控制面每次启动都清理上次遗留实例，确保平台以零漏洞场景的冷状态启动。 */
    @PostConstruct
    public void initializeColdState() {
        cleanupScenarioContainers();
    }

    /** Compose 优雅停止控制面时先删除按需容器，避免实验网络仍被占用。 */
    @PreDestroy
    public void shutdownScenarioContainers() {
        cleanupScenarioContainers();
    }

    /** 返回全部场景定义及其当前运行状态。 */
    public List<ScenarioResponse> list() {
        return registry.findAll().stream().map(this::toResponse).toList();
    }

    /** 返回指定场景及其当前运行状态。 */
    public ScenarioResponse get(String id) {
        return toResponse(registry.require(id));
    }

    /** 返回当前 Runtime API 实际指向的场景；尚未启动场景时返回 null。 */
    public ScenarioResponse current() {
        return proxy.getActiveScenario().map(this::byBackend).map(this::toResponse).orElse(null);
    }

    /** 返回控制面最近一次成功切换后期望激活的场景。 */
    public String expectedActiveScenario() {
        return expectedActiveScenario;
    }

    /** 判断是否正在执行受全局锁保护的初始化或切换事务。 */
    public boolean isSwitchInProgress() {
        return switchInProgress;
    }

    /**
     * 使用前端提交的白名单参数重建目标容器，验证后切流，并按用户选择停止旧容器。
     */
    public SwitchScenarioResponse activate(String id, Map<String, String> options, boolean stopPrevious,
            String clientIp) {
        long start = System.nanoTime();
        switchLock.lock();
        switchInProgress = true;
        String previousBackend = null;
        ScenarioDefinition target = null;
        boolean targetStarted = false;
        boolean routingMutated = false;
        try {
            target = registry.require(id);
            if (!target.isEnabled())
                throw new ScenarioUnhealthyException("场景已禁用：" + id);
            previousBackend = proxy.getActiveScenario().orElse(null);
            Map<String, String> environment = registry.resolveEnvironment(id, options);
            String previousId = toScenarioId(previousBackend);
            initializingScenarios.add(id);

            // 目标可能是已运行的备用容器，也可能是当前容器；先摘流再按新参数重建。
            proxy.disableBackend(target.getRuntime().getBackend());
            routingMutated = true;
            docker.prepareAndStart(target, environment);
            targetStarted = true;

            // 在启用目标前关闭全部其他路由，保证任意时刻最多只有一个可接流量后端。
            for (ScenarioDefinition scenario : registry.findAll()) {
                if (!scenario.getRuntime().getBackend().equals(target.getRuntime().getBackend()))
                    proxy.disableBackend(scenario.getRuntime().getBackend());
            }
            proxy.enableBackend(target.getRuntime().getBackend());
            awaitReady(target);
            validation.validateActivation(target);

            if (stopPrevious && previousBackend != null
                    && !previousBackend.equals(target.getRuntime().getBackend())) {
                ScenarioDefinition previous = byBackend(previousBackend);
                if (!docker.stopContainer(previous.getRuntime().getContainerName()))
                    throw new ScenarioUnhealthyException("新场景已启动，但旧场景容器关闭失败");
            }

            registry.applyEffectiveEnvironment(id, environment);
            expectedActiveScenario = id;
            String message = previousBackend == null ? "场景初始化并启动成功"
                    : stopPrevious && !previousBackend.equals(target.getRuntime().getBackend())
                            ? "场景切换成功，原场景已关闭"
                            : "场景切换成功，原场景保留运行但已停止接流量";
            audit.record("SCENARIO_SWITCH", previousId, id, "SUCCESS", message, clientIp, elapsed(start));
            return new SwitchScenarioResponse(previousId, id, message, Instant.now());
        } catch (RuntimeException failure) {
            if (routingMutated)
                rollback(previousBackend);
            if (targetStarted && target != null
                    && !target.getRuntime().getBackend().equals(previousBackend))
                docker.stopContainer(target.getRuntime().getContainerName());
            audit.record("SCENARIO_SWITCH", toScenarioId(previousBackend), id, "FAILED", failure.getMessage(),
                    clientIp, elapsed(start));
            if (failure instanceof ScenarioNotFoundException || failure instanceof ScenarioUnhealthyException
                    || failure instanceof VerificationException
                    || failure instanceof com.cryptolab.exception.ConfigurationValidationException)
                throw failure;
            throw new ProxySwitchException("场景初始化或切换失败，已尝试回滚：" + failure.getMessage(), failure);
        } finally {
            initializingScenarios.remove(id);
            switchInProgress = false;
            switchLock.unlock();
        }
    }

    /** 每 250 毫秒读取 Runtime 状态，等待 Docker DNS 和 HAProxy 健康检查完成。 */
    private void awaitReady(ScenarioDefinition target) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            String state = proxy.getRuntimeStatus().get(target.getRuntime().getBackend());
            if ("UP".equalsIgnoreCase(state) || "OPEN".equalsIgnoreCase(state))
                return;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProxySwitchException("场景切换等待被中断", e);
            }
        }
        throw new VerificationException("HAProxy 后端未在 30 秒内就绪");
    }

    /** 尽力恢复切换前的唯一可用后端；无旧场景时保持全部后端为维护状态。 */
    private void rollback(String previousBackend) {
        try {
            registry.findAll().forEach(scenario -> proxy.disableBackend(scenario.getRuntime().getBackend()));
            if (previousBackend != null) {
                ScenarioDefinition previous = byBackend(previousBackend);
                ContainerInspection inspection = docker.inspectContainer(previous.getRuntime().getContainerName());
                if (inspection.exists() && inspection.running())
                    proxy.enableBackend(previousBackend);
            }
        } catch (RuntimeException ignored) {
            // 原始异常始终是主要失败原因，审计日志会保留它。
        }
    }

    /** 将所有声明后端置为维护状态并删除其已知容器；单个清理失败不阻止应用启动或关闭。 */
    private void cleanupScenarioContainers() {
        for (ScenarioDefinition scenario : registry.findAll()) {
            try {
                proxy.disableBackend(scenario.getRuntime().getBackend());
            } catch (RuntimeException ignored) {
                // HAProxy 可能尚未启动或已经先停止，Docker 清理仍需继续。
            }
            try {
                docker.removeContainer(scenario.getRuntime().getContainerName());
            } catch (RuntimeException ignored) {
                // 下次启动会再次清理，不能因残留实验容器阻止控制面启动。
            }
        }
        expectedActiveScenario = null;
    }

    /** 计算从纳秒起点开始的耗时毫秒数。 */
    private long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** 通过 HAProxy server 名称反查场景定义。 */
    private ScenarioDefinition byBackend(String backend) {
        return registry.findAll().stream()
                .filter(scenario -> scenario.getRuntime().getBackend().equals(backend))
                .findFirst()
                .orElseThrow(() -> new ProxySwitchException("未知 HAProxy 后端：" + backend));
    }

    /** 将内部后端名称转换为公开场景 ID。 */
    private String toScenarioId(String backend) {
        return backend == null ? null : byBackend(backend).getId();
    }

    /** 聚合 Docker、HAProxy、初始化进度和参数状态，构造前端响应。 */
    private ScenarioResponse toResponse(ScenarioDefinition scenario) {
        ContainerInspection inspection = docker.inspectContainer(scenario.getRuntime().getContainerName());
        String haproxy;
        try {
            haproxy = proxy.getRuntimeStatus().getOrDefault(scenario.getRuntime().getBackend(), "UNKNOWN");
        } catch (RuntimeException e) {
            haproxy = "UNKNOWN";
        }
        ScenarioStatus status = initializingScenarios.contains(scenario.getId())
                ? ScenarioStatus.INITIALIZING
                : validation.statusOf(scenario);
        List<VulnerabilityOptionResponse> options = scenario.getVulnerabilityOptions().stream()
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
        String health = !inspection.exists() ? "not-created" : inspection.running() ? inspection.health() : "stopped";
        return new ScenarioResponse(scenario.getId(), scenario.getName(), scenario.getCve(), scenario.getDescription(),
                scenario.getCategory(), scenario.getRuntime().getContainerName(),
                scenario.getRuntime().getContainerPort(), scenario.getRuntime().getProtocol(), scenario.isEnabled(),
                haproxy, status, status == ScenarioStatus.ACTIVE, health, options, conditionsMet);
    }
}
