package com.mbank.service;

/**
 * 审计日志服务接口
 */
public interface AuditLogService {

    /**
     * 记录审计日志（独立事务，业务回滚不影响日志）
     *
     * @param userId   操作用户ID（可为 null）
     * @param action   操作类型
     * @param resource 操作资源
     * @param detail   操作详情（JSON）
     * @param ip       客户端 IP
     */
    void log(Long userId, String action, String resource, String detail, String ip);
}
