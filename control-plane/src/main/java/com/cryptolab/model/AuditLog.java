package com.cryptolab.model;

import jakarta.persistence.*;
import java.time.Instant;

/** 映射到 H2 audit_logs 表的一条不可变操作审计数据。 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String action, previousScenario, targetScenario, result;
    @Column(length = 2000)
    private String message;
    private String clientIp;
    private Instant timestamp;
    private long durationMs;

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String v) {
        action = v;
    }

    public String getPreviousScenario() {
        return previousScenario;
    }

    public void setPreviousScenario(String v) {
        previousScenario = v;
    }

    public String getTargetScenario() {
        return targetScenario;
    }

    public void setTargetScenario(String v) {
        targetScenario = v;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String v) {
        result = v;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String v) {
        message = v;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String v) {
        clientIp = v;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant v) {
        timestamp = v;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long v) {
        durationMs = v;
    }
}
