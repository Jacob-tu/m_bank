---
trigger: always_on
---
# 安全策略

## 认证与鉴权

### JWT 认证

- 算法：HS256
- 密钥长度：>= 256 位
- Access Token 有效期：24 小时
- Token 载荷：userId, phone, issuedAt, expiration
- Token 存储：前端 localStorage，请求 Header 携带

### 密码安全

- 存储方式：BCrypt 加密（强度因子 >= 10）
- 密码强度要求：
  - 长度 >= 8 位
  - 包含大写字母、小写字母、数字中的至少两种
- 传输安全：HTTPS（生产环境强制）
- 密码修改需验证旧密码

### 鉴权规则

| 资源 | 规则 |
|------|------|
| /api/v1/auth/** | 允许匿名访问 |
| /api/v1/users/** | 需要认证，只能访问自己的数据 |
| /api/v1/cards/** | 需要认证，只能操作自己的银行卡 |
| /api/v1/transactions/** | 需要认证，只能查看自己的交易 |

## 数据安全

### 敏感数据加密

| 数据类型 | 存储方式 | 展示方式 |
|----------|----------|----------|
| 密码 | BCrypt 哈希 | 不展示 |
| 手机号 | 明文存储 | 138****8000 |
| 银行卡号 | AES-256 加密 | **** **** **** 8888 |
| 身份证号 | AES-256 加密 | 不展示（预留） |
| 余额 | 明文存储 | 正常展示 |

### SQL 注入防护

- 必须使用参数化查询（JPA/Hibernate 天然防护）
- 禁止字符串拼接 SQL
- @Query 注解使用参数绑定：`WHERE u.phone = :phone`
- 输入校验：使用 JSR-303 Validation

### XSS 防护

- 后端：输入校验 + 输出编码
- 前端：不使用 v-html 渲染用户输入
- 前端：使用 Vue3 的模板语法（自动转义）
- 响应头：X-Content-Type-Options: nosniff

## 操作审计

### 审计日志记录范围

| 操作类型 | 必须审计 | 审计内容 |
|----------|----------|----------|
| 用户登录 | 是 | 用户ID、IP、时间、结果 |
| 用户注册 | 是 | 手机号、IP、时间 |
| 密码修改 | 是 | 用户ID、IP、时间 |
| 绑定银行卡 | 是 | 用户ID、卡号(脱敏)、时间 |
| 解绑银行卡 | 是 | 用户ID、卡号(脱敏)、时间 |
| 转账 | 是 | 用户ID、金额、来源卡、目标卡、时间 |
| 缴费 | 是 | 用户ID、金额、卡号(脱敏)、缴费类型、时间 |
| 限额设置 | 是 | 用户ID、卡号(脱敏)、原限额、新限额、时间 |

### 审计日志字段

```java
@Entity
public class AuditLog {
    private Long id;           // 主键
    private Long userId;       // 操作用户ID
    private String action;     // 操作类型
    private String resource;   // 操作资源
    private String detail;     // 操作详情（JSON）
    private String ip;         // 客户端IP
    private LocalDateTime time; // 操作时间
}
```

## CORS 策略

- 开发环境：允许 localhost
- 生产环境：仅允许指定域名
- 不允许 Credentials：false（JWT 方式无需 Cookie）
- 允许的方法：GET, POST, PUT, DELETE
- 允许的头部：Content-Type, Authorization

## 金融业务安全

### 转账安全

- 金额校验：amount > 0 且 amount <= 余额 且 amount <= 卡片限额
- 幂等性：通过唯一请求ID或数据库唯一约束防止重复扣款
- 事务完整性：扣款和入账在同一个事务中
- 越权检查：只能从自己的银行卡转出

### 并发安全

- 余额更新使用乐观锁或行级锁
- 防止并发扣款导致余额为负
