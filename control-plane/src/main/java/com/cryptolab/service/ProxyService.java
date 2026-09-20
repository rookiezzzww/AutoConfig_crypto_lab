package com.cryptolab.service;

import com.cryptolab.exception.ProxySwitchException;
import org.springframework.stereotype.Service;
import java.util.*;


/**
 * ProxyService 提供了与 HAProxy 运行时交互的功能，包括激活和禁用后端场景，
 * 以及获取运行时状态。它依赖于 HaproxyRuntimeClient 来执行命令并返回结果。
 */
/** 集中封装 HAProxy Runtime API 命令，禁止业务层直接操作 Socket。 */
@Service
public class ProxyService {
    public static final String BACKEND = "crypto_scenarios";
    private final HaproxyRuntimeClient client;

    /** 注入发送 Runtime 命令的客户端。 */
    public ProxyService(HaproxyRuntimeClient client) {
        this.client = client;
    }

    /** 将指定后端置为可用状态。 */
    public void activateScenario(String backend) {
        enableBackend(backend);
    }

    /** 将指定后端置为维护状态。 */
    public void disableBackend(String backend) {
        command("set server " + BACKEND + "/" + checked(backend) + " state maint");
    }

    /** 将指定后端置为 ready 状态。 */
    public void enableBackend(String backend) {
        command("set server " + BACKEND + "/" + checked(backend) + " state ready");
    }

    /** 执行 show stat，并解析实验后端的实时状态。 */
    public Map<String, String> getRuntimeStatus() {
        String csv = client.execute("show stat");
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = csv.split("\\r?\\n");
        if (lines.length < 2)
            return result;
        String[] header = lines[0].replaceFirst("^# ", "").split(",", -1);
        int px = index(header, "pxname"), sv = index(header, "svname"), status = index(header, "status");
        if (px < 0 || sv < 0 || status < 0)
            throw new ProxySwitchException("Unexpected HAProxy show stat response");
        for (int i = 1; i < lines.length; i++) {
            String[] row = lines[i].split(",", -1);
            if (row.length > Math.max(px, Math.max(sv, status)) && BACKEND.equals(row[px])
                    && !"BACKEND".equals(row[sv]))
                result.put(row[sv], row[status]);
        }
        return result;
    }

    /** 找到当前处于 UP 或 OPEN 状态的后端。 */
    public Optional<String> getActiveScenario() {
        return getRuntimeStatus().entrySet().stream()
                .filter(e -> "UP".equalsIgnoreCase(e.getValue()) || "OPEN".equalsIgnoreCase(e.getValue()))
                .map(Map.Entry::getKey).findFirst();
    }

    /** 定位 HAProxy CSV 表头字段下标。 */
    private int index(String[] values, String value) {
        for (int i = 0; i < values.length; i++)
            if (value.equals(values[i]))
                return i;
        return -1;
    }

    /** 执行命令并将错误响应转换为代理切换异常。 */
    private void command(String command) {
        String answer = client.execute(command);
        if (answer.toLowerCase(Locale.ROOT).contains("error") || answer.toLowerCase(Locale.ROOT).contains("unknown"))
            throw new ProxySwitchException("HAProxy rejected command: " + answer.trim());
    }

    /** 校验后端名称，防止非法文本进入 HAProxy 命令。 */
    private String checked(String value) {
        if (value == null || !value.matches("[a-z0-9_-]+"))
            throw new ProxySwitchException("Unsafe backend name");
        return value;
    }
}
