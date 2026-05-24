-- ================================================================
-- M-Bank 数据库初始化脚本
-- 注意：此脚本由 Docker Compose 在 MySQL 容器首次启动时执行
--       后续数据库版本升级由 Flyway 管理（db/migration/）
-- ================================================================

-- 创建数据库（已在 docker-compose.yml 中通过 MYSQL_DATABASE 环境变量创建）
-- 此处做幂等保护
CREATE DATABASE IF NOT EXISTS `mbank`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `mbank`;

-- 创建应用专用账号（最小权限原则）
-- 注意：root 密码通过 MYSQL_ROOT_PASSWORD 环境变量设置
-- 此处创建应用账号并授权
-- MYSQL_USER / MYSQL_PASSWORD 环境变量会自动创建，此处额外设置权限
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP
    ON `mbank`.*
    TO 'mbank'@'%';

FLUSH PRIVILEGES;

-- ================================================================
-- 核心业务表（与 Flyway V1__create_auth_tables.sql 保持一致）
-- Flyway 会在 Spring Boot 启动时自动执行迁移，无需重复建表
-- 此文件仅作为数据库初始化参考，Flyway 是唯一的 DDL 来源
-- ================================================================

-- 以下为注释形式的建表说明（实际建表由 Flyway 完成）

/*
-- 用户表 t_user
CREATE TABLE t_user (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    phone               VARCHAR(20)     NOT NULL,
    password            VARCHAR(100)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    failed_login_count  INT             NOT NULL DEFAULT 0,
    locked_until        DATETIME        NULL,
    password_changed_at DATETIME        NULL,
    mobile_verified_at  DATETIME        NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 资金账户表 t_account
CREATE TABLE t_account (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    balance     DECIMAL(18,2)   NOT NULL DEFAULT 0.00,
    status      VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    version     INT             NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审计日志表 t_audit_log
CREATE TABLE t_audit_log (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NULL,
    action      VARCHAR(50)     NOT NULL,
    resource    VARCHAR(50)     NOT NULL,
    detail      VARCHAR(500)    NULL,
    ip          VARCHAR(50)     NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
*/
