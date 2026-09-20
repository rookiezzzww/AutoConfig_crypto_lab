package com.cryptolab.model;

/** Docker inspect 的最小化、安全容器状态快照。 */
public record ContainerInspection(boolean exists, boolean running, boolean healthy, String health, String ipAddress,
        String network, String detail) {
    /** 创建表示 Docker 无法读取容器的不可用快照。 */
    public static ContainerInspection unavailable(String detail) {
        return new ContainerInspection(false, false, false, "unknown", null, null, detail);
    }
}
