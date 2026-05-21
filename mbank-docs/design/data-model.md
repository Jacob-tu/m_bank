# 数据模型设计 - 认证模块 MVP

## 文档信息

| 项目 | 内容 |
|------|------|
| 项目名称 | M-Bank 简易手机银行系统 |
| 模块名称 | 认证模块 |
| 版本 | v1.0 |
| 编写日期 | 2026-05-17 |
| 编写方式 | AI 原生（architect Agent） |

## 1. ER 关系图

```mermaid
erDiagram
    USER ||--|| ACCOUNT : "拥有"
    USER ||--o{ AUDIT_LOG : "产生"

    USER {
        bigint id PK "用户ID"
        varchar phone UK "手机号"
        varchar password "登录密码(BCrypt哈希)"
        varchar status "状态: NORMAL/LOCKED_TEMP/FROZEN"
        int failed_login_count "连续登录失败次数"
        datetime locked_until "临时锁定到期时间"
        datetime password_changed_at "最近密码修改时间"
        datetime created_at "注册时间"
        datetime updated_at "更新时间"
    }

    ACCOUNT {
        bigint id PK "账户ID"
        bigint user_id UK_FK "关联用户ID"
        decimal balance "可用余额"
        varchar status "账户状态"
        datetime created_at "开户时间"
        datetime updated_at "更新时间"
    }

    AUDIT_LOG {
        bigint id PK "日志ID"
        bigint user_id "操作用户ID(可为null)"
        varchar action "操作类型"
        varchar resource "操作资源"
        varchar detail "操作详情(JSON)"
        varchar ip "客户端IP"
        datetime created_at "操作时间"
    }
```

## 2. 表结构

### 2.1 t_user（用户表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 用户ID |
| phone | VARCHAR(20) | NOT NULL, UNIQUE | 手机号 |
| password | VARCHAR(100) | NOT NULL | 登录密码（BCrypt 哈希） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'NORMAL' | 用户状态：NORMAL / LOCKED_TEMP / FROZEN |
| failed_login_count | INT | NOT NULL, DEFAULT 0 | 连续登录失败次数 |
| locked_until | DATETIME | NULL | 临时锁定到期时间 |
| password_changed_at | DATETIME | NULL | 最近密码修改时间（用于 Token 失效判断） |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 注册时间 |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**状态机**：
- NORMAL -> LOCKED_TEMP：登录密码连续错误达到阈值（默认 5 次）
- LOCKED_TEMP -> NORMAL：锁定时长结束（默认 15 分钟）或人工解锁
- NORMAL / LOCKED_TEMP -> FROZEN：风控/运营冻结
- FROZEN -> NORMAL：人工审核通过

### 2.2 t_account（资金账户表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 账户ID |
| user_id | BIGINT | NOT NULL, UNIQUE, FK -> t_user(id) | 关联用户ID |
| balance | DECIMAL(18,2) | NOT NULL, DEFAULT 0.00 | 可用余额 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'NORMAL' | 账户状态 |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 开户时间 |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

### 2.3 t_audit_log（审计日志表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 日志ID |
| user_id | BIGINT | NULL | 操作用户ID（注册等匿名操作可为 null） |
| action | VARCHAR(50) | NOT NULL | 操作类型：REGISTER / LOGIN / LOGIN_FAILED / ACCOUNT_LOCKED / CHANGE_PASSWORD |
| resource | VARCHAR(50) | NOT NULL | 操作资源：USER |
| detail | VARCHAR(500) | NULL | 操作详情（JSON 格式，敏感数据需脱敏） |
| ip | VARCHAR(50) | NULL | 客户端 IP |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 操作时间 |

## 3. 索引设计

| 表名 | 索引名 | 字段 | 类型 | 说明 |
|------|--------|------|------|------|
| t_user | uk_user_phone | phone | UNIQUE | 手机号唯一约束，用于注册查重和登录查询 |
| t_account | uk_account_user_id | user_id | UNIQUE | 用户与账户一对一 |
| t_audit_log | idx_audit_user_id | user_id | NORMAL | 按用户查询审计记录 |
| t_audit_log | idx_audit_action | action | NORMAL | 按操作类型查询 |
| t_audit_log | idx_audit_created_at | created_at | NORMAL | 按时间范围查询 |

## 4. 数据库引擎与字符集

- 引擎：InnoDB（支持事务和外键）
- 字符集：utf8mb4（支持完整 Unicode）
- 排序规则：utf8mb4_general_ci

## 5. Flyway 迁移脚本

初始迁移脚本路径：`src/main/resources/db/migration/V1__create_auth_tables.sql`

该脚本创建以上 3 张表及所有索引。
