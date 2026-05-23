-- V1: 创建认证模块核心表
-- 引擎: InnoDB, 字符集: utf8mb4

-- ============================================================
-- 1. 用户表 t_user
-- ============================================================
CREATE TABLE t_user (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    phone               VARCHAR(20)     NOT NULL                COMMENT '手机号',
    password            VARCHAR(100)    NOT NULL                COMMENT '登录密码（BCrypt 哈希）',
    status              VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '状态: NORMAL/LOCKED_TEMP/FROZEN',
    failed_login_count  INT             NOT NULL DEFAULT 0      COMMENT '连续登录失败次数',
    locked_until        DATETIME        NULL                    COMMENT '临时锁定到期时间',
    password_changed_at DATETIME        NULL                    COMMENT '最近密码修改时间（Token 失效判断）',
    mobile_verified_at  DATETIME        NULL                    COMMENT '手机号验证时间',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ============================================================
-- 2. 资金账户表 t_account
-- ============================================================
CREATE TABLE t_account (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '账户ID',
    user_id     BIGINT          NOT NULL                COMMENT '关联用户ID',
    balance     DECIMAL(18,2)   NOT NULL DEFAULT 0.00   COMMENT '可用余额',
    status      VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '账户状态',
    version     INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开户时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_user_id (user_id),
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES t_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资金账户表';

-- ============================================================
-- 3. 审计日志表 t_audit_log
-- ============================================================
CREATE TABLE t_audit_log (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id     BIGINT          NULL                    COMMENT '操作用户ID（匿名操作可为 null）',
    action      VARCHAR(50)     NOT NULL                COMMENT '操作类型: REGISTER/LOGIN/LOGIN_FAILED/ACCOUNT_LOCKED/CHANGE_PASSWORD',
    resource    VARCHAR(50)     NOT NULL                COMMENT '操作资源: USER',
    detail      VARCHAR(500)    NULL                    COMMENT '操作详情（JSON，敏感数据脱敏）',
    ip          VARCHAR(50)     NULL                    COMMENT '客户端 IP',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_audit_user_id  (user_id),
    KEY idx_audit_action   (action),
    KEY idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审计日志表';
