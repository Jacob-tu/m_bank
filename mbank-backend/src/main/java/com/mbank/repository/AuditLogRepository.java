package com.mbank.repository;

import com.mbank.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审计日志数据访问层
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
