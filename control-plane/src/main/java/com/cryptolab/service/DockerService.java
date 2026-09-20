package com.cryptolab.service;

import com.cryptolab.config.LabProperties;
import com.cryptolab.model.ContainerInspection;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * DockerService 提供了与 Docker 容器交互的功能，包括检查容器状态、启动和停止容器。
 * 它依赖于 LabProperties 来确定是否启用 Docker 集成。
 */
/** 通过受限 Docker CLI 查询预热容器状态，并为后续冷池预留启停接口。 */
@Service
public class DockerService {
    private final LabProperties properties;

    /** 注入 Docker 集成功能开关。 */
    public DockerService(LabProperties properties) {
        this.properties = properties;
    }

    /** 执行 docker inspect 并转换为内部容器状态快照。 */
    public ContainerInspection inspectContainer(String name) {
        if (!properties.isDockerEnabled())
            return ContainerInspection.unavailable("Docker integration disabled");
        try {
            Process process = new ProcessBuilder("docker", "inspect", "--format",
                    "{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}|{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}",
                    name).redirectErrorStream(true).start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return ContainerInspection.unavailable("docker inspect timed out");
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0)
                return ContainerInspection.unavailable(out);
            String[] values = out.split("\\|", -1);
            boolean running = values.length > 0 && Boolean.parseBoolean(values[0]);
            String health = values.length > 1 ? values[1] : "none";
            boolean healthy = running && ("healthy".equalsIgnoreCase(health) || "none".equalsIgnoreCase(health));
            return new ContainerInspection(true, running, healthy, health, values.length > 2 ? values[2] : null,
                    "crypto-lab-network", out);
        } catch (Exception e) {
            return ContainerInspection.unavailable("docker inspect failed: " + e.getMessage());
        }
    }

    /** 启动指定容器，供后续 ondemand 场景使用。 */
    public boolean startContainer(String name) {
        return command("start", name);
    }

    /** 停止指定容器，供后续场景回收使用。 */
    public boolean stopContainer(String name) {
        return command("stop", name);
    }

    /** 运行受限的 docker start 或 docker stop 子进程。 */
    private boolean command(String action, String name) {
        if (!properties.isDockerEnabled())
            return false;
        try {
            Process p = new ProcessBuilder("docker", action, name).start();
            return p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
