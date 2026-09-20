package com.cryptolab.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative scenario document loaded from each scenario directory's YAML
 * file.
 */
/** 对应 scenario.yml 的完整声明式场景模型。 */
public class ScenarioDefinition {
    private String id, name, cve, description;
    private List<String> category = new ArrayList<>();
    private ScenarioRuntime runtime = new ScenarioRuntime();
    private SwitchSpec switchSpec = new SwitchSpec();
    private HealthCheck healthCheck = new HealthCheck();
    private Verification verification = new Verification();
    private List<VulnerabilityOption> vulnerabilityOptions = new ArrayList<>();
    private boolean enabled;

    public String getId() {
        return id;
    }

    public void setId(String v) {
        id = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getCve() {
        return cve;
    }

    public void setCve(String v) {
        cve = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public List<String> getCategory() {
        return category;
    }

    public void setCategory(List<String> v) {
        category = v;
    }

    public ScenarioRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(ScenarioRuntime v) {
        runtime = v;
    }

    public SwitchSpec getSwitch() {
        return switchSpec;
    }

    public void setSwitch(SwitchSpec v) {
        switchSpec = v;
    }

    public HealthCheck getHealthCheck() {
        return healthCheck;
    }

    public void setHealthCheck(HealthCheck v) {
        healthCheck = v;
    }

    public Verification getVerification() {
        return verification;
    }

    public void setVerification(Verification v) {
        verification = v;
    }

    public List<VulnerabilityOption> getVulnerabilityOptions() {
        return vulnerabilityOptions;
    }

    public void setVulnerabilityOptions(List<VulnerabilityOption> vulnerabilityOptions) {
        this.vulnerabilityOptions = vulnerabilityOptions == null ? new ArrayList<>() : vulnerabilityOptions;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    /** 描述预热或按需启动的切换策略。 */
    public static class SwitchSpec {
        private String mode;

        public String getMode() {
            return mode;
        }

        public void setMode(String v) {
            mode = v;
        }
    }

    /** 描述容器服务健康检查参数。 */
    public static class HealthCheck {
        private String type;
        private int port;

        public String getType() {
            return type;
        }

        public void setType(String v) {
            type = v;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int v) {
            port = v;
        }
    }

    /** 描述激活后使用的验证器类型。 */
    public static class Verification {
        private String type;

        public String getType() {
            return type;
        }

        public void setType(String v) {
            type = v;
        }
    }
}
