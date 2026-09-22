package com.cryptolab.service;

import com.cryptolab.config.LabProperties;
import com.cryptolab.exception.ScenarioUnhealthyException;
import com.cryptolab.model.ContainerInspection;
import com.cryptolab.model.ScenarioDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** 通过受限 Docker CLI 按需构建、创建、检查和停止漏洞场景容器。 */
@Service
public class DockerService {
    private static final long BUILD_TIMEOUT_SECONDS = 1_200;
    private final LabProperties properties;

    /** 注入 Docker 开关、场景目录和隔离网络配置。 */
    public DockerService(LabProperties properties) {
        this.properties = properties;
    }

    /** 执行 docker inspect，并转换为控制面使用的容器快照。 */
    public ContainerInspection inspectContainer(String name) {
        if (!properties.isDockerEnabled())
            return ContainerInspection.unavailable("Docker integration disabled");
        try {
            CommandResult result = run(List.of("docker", "inspect", "--format",
                    "{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}|{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}",
                    checkedName(name)), 4, false);
            if (!result.success())
                return ContainerInspection.unavailable(result.output());
            String[] values = result.output().trim().split("\\|", -1);
            boolean running = values.length > 0 && Boolean.parseBoolean(values[0]);
            String health = values.length > 1 ? values[1] : "none";
            boolean healthy = running && ("healthy".equalsIgnoreCase(health) || "none".equalsIgnoreCase(health));
            return new ContainerInspection(true, running, healthy, health, values.length > 2 ? values[2] : null,
                    properties.getScenarioNetwork(), result.output());
        } catch (RuntimeException e) {
            return ContainerInspection.unavailable("docker inspect failed: " + e.getMessage());
        }
    }

    /** 确保镜像存在，并使用已校验参数创建和启动全新的隔离容器。 */
    public void prepareAndStart(ScenarioDefinition scenario, Map<String, String> environment) {
        if (!properties.isDockerEnabled())
            throw new ScenarioUnhealthyException("Docker 集成已禁用，无法启动漏洞场景");
        String id = checkedName(scenario.getId());
        String image = checkedImage(scenario.getRuntime().getImage());
        String container = checkedName(scenario.getRuntime().getContainerName());
        ensureImage(image, id);
        removeContainer(container);

        List<String> create = new ArrayList<>(List.of("docker", "create",
                "--name", container,
                "--network", properties.getScenarioNetwork(),
                "--network-alias", id,
                "--cpus", "0.50",
                "--memory", "256m",
                "--pids-limit", "100",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges:true",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=16m"));
        environment.forEach((key, value) -> {
            create.add("--env");
            create.add(key + "=" + value);
        });
        create.add(image);
        requireSuccess(create, 30, "创建场景容器失败");
        requireSuccess(List.of("docker", "start", container), 30, "启动场景容器失败");
        awaitRunning(container);
    }

    /** 启动一个已经存在的容器。 */
    public boolean startContainer(String name) {
        return runQuietly(List.of("docker", "start", checkedName(name)), 30);
    }

    /** 停止一个正在运行的容器。 */
    public boolean stopContainer(String name) {
        return runQuietly(List.of("docker", "stop", "--time", "10", checkedName(name)), 20);
    }

    /** 强制删除指定场景容器，用于参数重建、冷启动初始化和优雅关闭。 */
    public void removeContainer(String name) {
        String checked = checkedName(name);
        ContainerInspection existing = inspectContainer(checked);
        if (existing.exists())
            requireSuccess(List.of("docker", "rm", "--force", checked), 30, "删除旧场景容器失败");
    }

    /** 镜像不存在时，使用只读挂载的场景目录作为构建上下文进行首次按需构建。 */
    private void ensureImage(String image, String scenarioId) {
        CommandResult inspect = run(List.of("docker", "image", "inspect", "--format", "{{.Id}}", image), 10, false);
        if (inspect.success())
            return;
        Path context = Path.of(properties.getScenariosPath(), scenarioId).normalize();
        requireSuccess(List.of("docker", "build", "--tag", image, context.toString()),
                BUILD_TIMEOUT_SECONDS, "首次构建漏洞镜像失败");
    }

    /** 等待容器进入运行状态；入口脚本立即退出时返回失败。 */
    private void awaitRunning(String container) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            ContainerInspection inspection = inspectContainer(container);
            if (inspection.exists() && inspection.running() && inspection.healthy())
                return;
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScenarioUnhealthyException("等待场景容器启动时被中断");
            }
        }
        throw new ScenarioUnhealthyException("场景容器未在 30 秒内进入运行状态：" + container);
    }

    /** 执行命令并在失败时返回适合前端显示的业务错误。 */
    private void requireSuccess(List<String> command, long timeoutSeconds, String message) {
        CommandResult result = run(command, timeoutSeconds, true);
        if (!result.success())
            throw new ScenarioUnhealthyException(message + (result.output().isBlank() ? "" : "：" + result.output()));
    }

    /** 运行不需要错误详情的简单启停命令。 */
    private boolean runQuietly(List<String> command, long timeoutSeconds) {
        if (!properties.isDockerEnabled())
            return false;
        try {
            return run(command, timeoutSeconds, true).success();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 启动 Docker 子进程；长时间操作直接输出日志，查询命令捕获结果。 */
    private CommandResult run(List<String> command, long timeoutSeconds, boolean inheritOutput) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (inheritOutput)
                builder.inheritIO();
            Process process = builder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, "命令执行超时");
            }
            String output = inheritOutput ? ""
                    : new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new CommandResult(process.exitValue() == 0, output);
        } catch (IOException e) {
            throw new ScenarioUnhealthyException("无法调用 Docker：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScenarioUnhealthyException("Docker 操作被中断");
        }
    }

    /** 限制容器、场景和网络别名，防止用户文本进入 Docker 参数。 */
    private String checkedName(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]+"))
            throw new ScenarioUnhealthyException("不安全的 Docker 名称：" + value);
        return value;
    }

    /** 限制镜像引用为 Docker 常见的仓库、标签和摘要字符。 */
    private String checkedImage(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._/@:-]+"))
            throw new ScenarioUnhealthyException("不安全的镜像名称：" + value);
        return value;
    }

    /** Docker 命令的最小结果对象。 */
    private record CommandResult(boolean success, String output) {
    }
}
