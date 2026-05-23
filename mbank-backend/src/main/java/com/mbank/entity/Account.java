package com.mbank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金账户实体
 * 对应数据库表 t_account，与 User 一对一关联
 */
@Getter
@Setter
@Entity
@Table(name = "t_account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联用户ID（唯一） */
    @Column(nullable = false, unique = true)
    private Long userId;

    /** 可用余额 */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    /** 账户状态 */
    @Column(nullable = false, length = 20)
    private String status = "NORMAL";

    /** 乐观锁版本号（SF-03） */
    @Version
    @Column(nullable = false)
    private int version = 0;

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
