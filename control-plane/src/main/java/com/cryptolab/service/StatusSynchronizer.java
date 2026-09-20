package com.cryptolab.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 
 * StatusSynchronizer 用于同步 HAProxy 和控制平面之间的状态。它会定期检查 
 * HAProxy 的活动场景是否与控制平面的预期状态一致。
 */

/** 每五秒检测控制面期望状态和 HAProxy 实际状态是否漂移。 */
@Component
public class StatusSynchronizer {
    private final ScenarioService scenarios;
    private final ProxyService proxy;
    private final AuditService audit;
    private volatile String lastDrift;

    /** 注入状态来源与审计服务。 */
    public StatusSynchronizer(ScenarioService scenarios, ProxyService proxy, AuditService audit) {
        this.scenarios = scenarios;
        this.proxy = proxy;
        this.audit = audit;
    }

    /** 定期检测漂移；切换事务进行时跳过，且绝不自动修复路由。 */
    @Scheduled(fixedDelay = 5000)
    public void synchronize() {
        try {
            // During a locked switch HAProxy intentionally has a short transitional state.
            if (scenarios.isSwitchInProgress())
                return;
            String actual = proxy.getActiveScenario().orElse("none");
            String expected = scenarios.expectedActiveScenario();
            if (expected == null)
                return;
            String drift = expected.equals(actual) ? null : expected + "->" + actual;
            if (drift != null && !drift.equals(lastDrift))
                audit.record("STATE_DRIFT", expected, actual, "FAILED",
                        "HAProxy state differs from control-plane expectation", "system", 0);
            lastDrift = drift;
        } catch (RuntimeException ignored) {
        }
    }
}
