package com.cryptolab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptolab.exception.ConfigurationValidationException;
import com.cryptolab.exception.ScenarioNotFoundException;
import com.cryptolab.exception.VerificationException;
import com.cryptolab.model.ContainerInspection;
import com.cryptolab.model.ScenarioDefinition;
import com.cryptolab.model.ScenarioRuntime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证按需初始化、切换、旧容器选择和失败回滚。 */
class ScenarioServiceTest {
    @Test
    void initializesFirstScenarioWithSubmittedEnvironment() {
        Fixture fixture = new Fixture();
        when(fixture.proxy.getActiveScenario()).thenReturn(Optional.empty());

        var response = fixture.service.activate("poodle", Map.of("protocol", "ssl3"), false, "test");

        assertEquals("poodle", response.currentScenario());
        verify(fixture.docker).prepareAndStart(fixture.poodle, Map.of("POODLE_PROTOCOL", "ssl3"));
        verify(fixture.proxy).enableBackend("poodle");
        verify(fixture.registry).applyEffectiveEnvironment("poodle", Map.of("POODLE_PROTOCOL", "ssl3"));
    }

    @Test
    void switchesAndStopsPreviousContainerWhenRequested() {
        Fixture fixture = new Fixture();

        fixture.service.activate("poodle", Map.of("protocol", "ssl3"), true, "test");

        verify(fixture.docker).stopContainer("crypto-heartbleed");
        verify(fixture.audit).record(eq("SCENARIO_SWITCH"), eq("heartbleed"), eq("poodle"), eq("SUCCESS"),
                anyString(), eq("test"), anyLong());
    }

    @Test
    void keepsPreviousContainerRunningWhenRequested() {
        Fixture fixture = new Fixture();

        fixture.service.activate("poodle", Map.of("protocol", "ssl3"), false, "test");

        verify(fixture.docker, never()).stopContainer("crypto-heartbleed");
        verify(fixture.proxy).disableBackend("heartbleed");
    }

    @Test
    void rejectsInvalidConfigurationBeforeDockerStart() {
        Fixture fixture = new Fixture();
        when(fixture.registry.resolveEnvironment("poodle", Map.of("protocol", "bad")))
                .thenThrow(new ConfigurationValidationException("配置错误"));

        assertThrows(ConfigurationValidationException.class,
                () -> fixture.service.activate("poodle", Map.of("protocol", "bad"), false, "test"));
        verify(fixture.docker, never()).prepareAndStart(any(), any());
    }

    @Test
    void reportsMissingTarget() {
        Fixture fixture = new Fixture();
        when(fixture.registry.require("missing")).thenThrow(new ScenarioNotFoundException("missing"));

        assertThrows(ScenarioNotFoundException.class,
                () -> fixture.service.activate("missing", Map.of(), false, "test"));
    }

    @Test
    void rollsBackAndStopsNewTargetWhenValidationFails() {
        Fixture fixture = new Fixture();
        doThrow(new VerificationException("bad")).when(fixture.validation).validateActivation(fixture.poodle);

        assertThrows(VerificationException.class,
                () -> fixture.service.activate("poodle", Map.of("protocol", "ssl3"), false, "test"));
        verify(fixture.proxy).enableBackend("heartbleed");
        verify(fixture.docker).stopContainer("crypto-poodle");
    }

    @Test
    void removesKnownScenarioContainersDuringLifecycleCleanup() {
        Fixture fixture = new Fixture();

        fixture.service.shutdownScenarioContainers();

        verify(fixture.docker).removeContainer("crypto-heartbleed");
        verify(fixture.docker).removeContainer("crypto-poodle");
        verify(fixture.proxy).disableBackend("heartbleed");
        verify(fixture.proxy).disableBackend("poodle");
    }

    /** 创建具有确定 Docker 和 HAProxy 状态的测试夹具。 */
    static class Fixture {
        final ScenarioRegistry registry = mock(ScenarioRegistry.class);
        final DockerService docker = mock(DockerService.class);
        final ProxyService proxy = mock(ProxyService.class);
        final ValidationService validation = mock(ValidationService.class);
        final AuditService audit = mock(AuditService.class);
        final ScenarioDefinition heart = definition("heartbleed", "crypto-heartbleed");
        final ScenarioDefinition poodle = definition("poodle", "crypto-poodle");
        final ScenarioService service;

        Fixture() {
            when(registry.require("poodle")).thenReturn(poodle);
            when(registry.findAll()).thenReturn(List.of(heart, poodle));
            when(registry.resolveEnvironment("poodle", Map.of("protocol", "ssl3")))
                    .thenReturn(Map.of("POODLE_PROTOCOL", "ssl3"));
            when(docker.inspectContainer(anyString()))
                    .thenReturn(new ContainerInspection(true, true, true, "healthy", null, null, ""));
            when(docker.stopContainer(anyString())).thenReturn(true);
            when(proxy.getActiveScenario()).thenReturn(Optional.of("heartbleed"));
            when(proxy.getRuntimeStatus()).thenReturn(Map.of("heartbleed", "MAINT", "poodle", "UP"));
            service = new ScenarioService(registry, docker, proxy, validation, audit);
        }

        /** 创建测试所需的最小场景定义。 */
        static ScenarioDefinition definition(String id, String container) {
            ScenarioDefinition scenario = new ScenarioDefinition();
            scenario.setId(id);
            scenario.setName(id);
            scenario.setCve("CVE-x");
            scenario.setEnabled(true);
            ScenarioRuntime runtime = new ScenarioRuntime();
            runtime.setImage("crypto-lab/" + id + ":1.0");
            runtime.setContainerName(container);
            runtime.setBackend(id);
            scenario.setRuntime(runtime);
            return scenario;
        }
    }
}
