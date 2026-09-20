package com.cryptolab.repository;

import com.cryptolab.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 审计日志的 Spring Data JPA 仓库。 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    /** 按时间倒序最多查询一百条记录。 */
    List<AuditLog> findTop100ByOrderByTimestampDesc();
}
