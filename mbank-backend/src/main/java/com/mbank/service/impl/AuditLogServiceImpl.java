package com.mbank.service.impl;

import com.mbank.entity.AuditLog;
import com.mbank.repository.AuditLogRepository;
import com.mbank.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志服务实现
 * 使用 REQUIRES_NEW 独立事务，确保业务回滚不丢失审计记录
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 记录审计日志
     * REQUIRES_NEW: 无论外层事务是否回滚，日志必须持久化
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String resource, String detail, String ip) {
        AuditLog auditLog = new AuditLog(userId, action, resource, detail, ip);
        auditLogRepository.save(auditLog);
    }
}
