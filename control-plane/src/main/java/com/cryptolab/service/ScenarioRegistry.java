package com.cryptolab.service;

import com.cryptolab.config.LabProperties;
import com.cryptolab.model.ScenarioDefinition;
import com.cryptolab.model.VulnerabilityOption;
import com.cryptolab.exception.ProxySwitchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
/**
 * 
 * ScenarioRegistry用来加载和管理场景定义。它从指定的目录中读取 scenario.yml 文件，
 * 并将其解析为 ScenarioDefinition 对象。该服务提供了查找和获取场景定义的方法。
 */
/** 扫描并校验 scenario.yml，将声明式配置注册为内存中的场景定义。 */
@Service
public class ScenarioRegistry {
    private final LabProperties properties;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<String, ScenarioDefinition> scenarios = new LinkedHashMap<>();
    private final Map<String, Path> vulnerabilityOptionFiles = new LinkedHashMap<>();

    /** 注入场景根目录配置。 */
    public ScenarioRegistry(LabProperties properties) {
        this.properties = properties;
    }

    /** 在 Spring Bean 初始化后加载全部场景 YAML。 */
    @PostConstruct
    public synchronized void load() {
        scenarios.clear();
        vulnerabilityOptionFiles.clear();
        Path root = Paths.get(properties.getScenariosPath());
        if (!Files.isDirectory(root))
            throw new ProxySwitchException("Scenario directory is missing: " + root);
        try (var paths = Files.walk(root, 2)) {
            paths.filter(p -> p.getFileName().toString().equals("scenario.yml")).sorted().forEach(this::readScenario);
        } catch (IOException e) {
            throw new ProxySwitchException("Unable to scan scenario directory", e);
        }
        if (scenarios.isEmpty())
            throw new ProxySwitchException("No scenario.yml files found in " + root);
    }

    /** 读取并注册一个 YAML 文件，同时拒绝重复 ID。 */
    private void readScenario(Path file) {
        try {
            ScenarioDefinition definition = yaml.readValue(file.toFile(), ScenarioDefinition.class);
            validate(definition, file);
            resolveVulnerabilityOptions(definition, file.getParent().resolve("vulnerability.env"));
            if (scenarios.putIfAbsent(definition.getId(), definition) != null)
                throw new ProxySwitchException("Duplicate scenario id: " + definition.getId());
            vulnerabilityOptionFiles.put(definition.getId(), file.getParent().resolve("vulnerability.env"));
        } catch (IOException e) {
            throw new ProxySwitchException("Invalid scenario YAML: " + file, e);
        }
    }

    /** 验证场景定义的切换所需核心字段。 */
    private void validate(ScenarioDefinition d, Path file) {
        if (blank(d.getId()) || blank(d.getName()) || blank(d.getCve()) || d.getRuntime() == null
                || blank(d.getRuntime().getContainerName()) || blank(d.getRuntime().getBackend())
                || d.getRuntime().getContainerPort() < 1 || d.getRuntime().getContainerPort() > 65535
                || d.getSwitch() == null || blank(d.getSwitch().getMode()))
            throw new ProxySwitchException("Incomplete scenario definition: " + file);
    }

    /**
     * 读取场景目录中的 vulnerability.env，校验白名单后写入参数有效值。
     * 未声明的变量会被拒绝，避免拼写错误或把普通应用配置混入漏洞配置。
     */
    private void resolveVulnerabilityOptions(ScenarioDefinition definition, Path envFile) {
        Properties configured = new Properties();
        if (Files.exists(envFile)) {
            try (var reader = Files.newBufferedReader(envFile)) {
                configured.load(reader);
            } catch (IOException e) {
                throw new ProxySwitchException("Unable to read vulnerability options: " + envFile, e);
            }
        }

        Set<String> declaredVariables = new HashSet<>();
        Set<String> optionKeys = new HashSet<>();
        for (VulnerabilityOption option : definition.getVulnerabilityOptions()) {
            if (blank(option.getKey()) || blank(option.getName()) || blank(option.getEnvVar())
                    || blank(option.getDefaultValue())
                    || (blank(option.getVulnerableValue()) && option.getVulnerableValues().isEmpty())) {
                throw new ProxySwitchException("Incomplete vulnerability option in " + definition.getId());
            }
            if (!optionKeys.add(option.getKey()))
                throw new ProxySwitchException("Duplicate vulnerability option key: " + option.getKey());
            if (!declaredVariables.add(option.getEnvVar()))
                throw new ProxySwitchException("Duplicate vulnerability environment variable: " + option.getEnvVar());
            if (!option.getEnvVar().matches("[A-Z][A-Z0-9_]*"))
                throw new ProxySwitchException("Unsafe vulnerability environment variable: " + option.getEnvVar());
            if ("enum".equalsIgnoreCase(option.getType()) && option.getAllowedValues().isEmpty())
                throw new ProxySwitchException("Enum vulnerability option requires allowedValues: " + option.getKey());
            if (!option.getAllowedValues().isEmpty() && !option.getAllowedValues().contains(option.getDefaultValue()))
                throw new ProxySwitchException("Default value is outside allowedValues: " + option.getKey());
            if (!blank(option.getVulnerableValue()) && !option.getAllowedValues().isEmpty()
                    && !option.getAllowedValues().contains(option.getVulnerableValue()))
                throw new ProxySwitchException("Vulnerable value is outside allowedValues: " + option.getKey());
            if (!option.getAllowedValues().isEmpty()
                    && !option.getAllowedValues().containsAll(option.getVulnerableValues()))
                throw new ProxySwitchException("Vulnerable values are outside allowedValues: " + option.getKey());

            String value = configured.getProperty(option.getEnvVar(), option.getDefaultValue()).trim();
            if (!option.getAllowedValues().isEmpty() && !option.getAllowedValues().contains(value)) {
                throw new ProxySwitchException("Invalid value for " + option.getEnvVar() + ": " + value
                        + ", allowed values: " + option.getAllowedValues());
            }
            option.setEffectiveValue(value);
        }

        for (String variable : configured.stringPropertyNames()) {
            if (!declaredVariables.contains(variable))
                throw new ProxySwitchException("Undeclared vulnerability option in " + envFile + ": " + variable);
        }
    }

    /** 判断配置文本是否为空。 */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 返回不可变的全部场景快照。 */
    public synchronized Collection<ScenarioDefinition> findAll() {
        refreshVulnerabilityOptions();
        return List.copyOf(scenarios.values());
    }

    /** 按 ID 尝试查找场景。 */
    public synchronized Optional<ScenarioDefinition> find(String id) {
        refreshVulnerabilityOptions();
        return Optional.ofNullable(scenarios.get(id));
    }

    /** 按 ID 获取场景；不存在时抛出统一业务异常。 */
    public ScenarioDefinition require(String id) {
        return find(id).orElseThrow(() -> new com.cryptolab.exception.ScenarioNotFoundException(id));
    }

    /** 重新读取参数文件，使重建单个漏洞容器后无需重启控制面。 */
    private void refreshVulnerabilityOptions() {
        vulnerabilityOptionFiles.forEach((id, file) -> resolveVulnerabilityOptions(scenarios.get(id), file));
    }
}
