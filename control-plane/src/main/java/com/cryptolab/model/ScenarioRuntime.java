package com.cryptolab.model;

/** 保存场景镜像、容器和 HAProxy 后端等运行时映射。 */
public class ScenarioRuntime {
    private String image, containerName, protocol, backend;
    private int containerPort;

    public String getImage() {
        return image;
    }

    public void setImage(String v) {
        image = v;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String v) {
        containerName = v;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String v) {
        protocol = v;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String v) {
        backend = v;
    }

    public int getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(int v) {
        containerPort = v;
    }
}
