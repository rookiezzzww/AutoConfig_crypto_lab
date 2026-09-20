package com.cryptolab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LabProperties 是一个配置类，用于加载和管理与实验室相关的属性。它使用 @ConfigurationProperties 
 * 注解来绑定前缀为 "lab" 的配置属性。该类包含了实验室的场景路径、HAProxy 套接字路径、
 * 代理主机和端口，以及是否启用Docker集成的标志。
 */
@ConfigurationProperties(prefix = "lab")
public class LabProperties {
    private String scenariosPath;
    private String haproxySocket;
    private String proxyHost;
    private int proxyPort = 8443;
    private boolean dockerEnabled = true;
    public String getScenariosPath() { return scenariosPath; }
    public void setScenariosPath(String scenariosPath) { this.scenariosPath = scenariosPath; }
    public String getHaproxySocket() { return haproxySocket; }
    public void setHaproxySocket(String haproxySocket) { this.haproxySocket = haproxySocket; }
    public String getProxyHost() { return proxyHost; }
    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
    public boolean isDockerEnabled() { return dockerEnabled; }
    public void setDockerEnabled(boolean dockerEnabled) { this.dockerEnabled = dockerEnabled; }
}
