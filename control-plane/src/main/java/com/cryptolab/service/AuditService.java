package com.cryptolab.service;

import com.cryptolab.model.AuditLog;
import com.cryptolab.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
/**
 * 用来记录审计日志的服务类，提供记录和查询审计日志的功能。
 */
/** 将切换、验证和漂移事件持久化到 H2 审计表。 */
@Service
public class AuditService {
    private final AuditLogRepository repository;

    /** 注入审计日志仓库。 */
    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** 创建并保存一条完整的审计记录。 */
    public void record(String action, String previous, String target, String result, String message, String clientIp,
            long durationMs) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setPreviousScenario(previous);
        log.setTargetScenario(target);
        log.setResult(result);
        log.setMessage(message);
        log.setClientIp(clientIp);
        log.setTimestamp(Instant.now());
        log.setDurationMs(durationMs);
        repository.save(log);
    }

    /** 返回最近一百条审计日志。 */
    public List<AuditLog> recent() {
        return repository.findTop100ByOrderByTimestampDesc();
    }
}
