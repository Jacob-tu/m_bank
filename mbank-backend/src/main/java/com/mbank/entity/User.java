package com.mbank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户实体
 * 对应数据库表 t_user
 */
@Getter
@Setter
@Entity
@Table(name = "t_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 手机号（唯一） */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    /** 登录密码（BCrypt 哈希） */
    @Column(nullable = false, length = 100)
    private String password;

    /**
     * 用户状态
     * NORMAL: 正常
     * LOCKED_TEMP: 临时锁定（连续登录失败）
     * FROZEN: 冻结
     */
    @Column(nullable = false, length = 20)
    private String status = "NORMAL";

    /** 连续登录失败次数 */
    @Column(nullable = false)
    private int failedLoginCount = 0;

    /** 临时锁定到期时间 */
    @Column
    private LocalDateTime lockedUntil;

    /** 最近密码修改时间（用于 Token 失效判断） */
    @Column
    private LocalDateTime passwordChangedAt;

    /** 手机号验证时间（SF-02） */
    @Column
    private LocalDateTime mobileVerifiedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
