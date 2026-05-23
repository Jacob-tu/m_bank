package com.mbank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 * 对应数据库表 t_audit_log
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户ID（匿名操作可为 null） */
    @Column
    private Long userId;

    /** 操作类型：REGISTER / LOGIN / LOGIN_FAILED / ACCOUNT_LOCKED / CHANGE_PASSWORD */
    @Column(nullable = false, length = 50)
    private String action;

    /** 操作资源：USER */
    @Column(nullable = false, length = 50)
    private String resource;

    /** 操作详情（JSON，敏感数据脱敏） */
    @Column(length = 500)
    private String detail;

    /** 客户端 IP */
    @Column(length = 50)
    private String ip;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public AuditLog(Long userId, String action, String resource, String detail, String ip) {
        this.userId = userId;
        this.action = action;
        this.resource = resource;
        this.detail = detail;
        this.ip = ip;
    }
}
