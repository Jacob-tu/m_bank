# MVP Core - 后端认证模块实现计划

## Context

M-Bank 项目已完成需求文档(V1.3)和项目基础设施配置(.qoder 规则、工作流、Skill)，但尚无任何实际代码。用户希望先实现最小核心功能集来验证后端架构，选择了**仅后端认证模块**作为第一个迭代，用 Postman/curl 验证。

**目标**: 实现用户注册、登录、获取用户信息、修改密码 4 个 API，验证 Spring Boot + JPA + MySQL + Spring Security + JWT 的技术架构是否跑通。

---

## 实现范围

| API | Method | URL | 认证 |
|-----|--------|-----|------|
| 用户注册 | POST | /api/v1/auth/register | 否 |
| 用户登录 | POST | /api/v1/auth/login | 否 |
| 获取当前用户 | GET | /api/v1/users/me | 是 |
| 修改密码 | PUT | /api/v1/users/me/password | 是 |

---

## 实现步骤 (严格按依赖顺序)

### Step 1: 项目骨架 (2 files)

**1.1** `mbank-backend/pom.xml`
- parent: spring-boot-starter-parent 3.2.5
- java.version: 17
- 依赖: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-validation, mysql-connector-j(runtime), flyway-core + flyway-mysql, jjwt-api/impl/jackson 0.12.5, lombok(provided), spring-boot-starter-test(test)

**1.2** `mbank-backend/src/main/java/com/mbank/MBankApplication.java`
- @SpringBootApplication 入口类

### Step 2: 配置 (1 file)

**2.1** `mbank-backend/src/main/resources/application.yml`
- server.port: 8080
- spring.datasource: MySQL localhost:3306/mbank
- spring.jpa: hibernate.ddl-auto=validate, show-sql=true
- spring.flyway: enabled=true
- mbank.jwt.secret / expiration-hours(24)
- mbank.security.max-login-attempts(5) / lock-duration-minutes(15)
- mbank.sms.mock-enabled(true) / valid-code("123456")

### Step 3: 数据库迁移 (1 file)

**3.1** `mbank-backend/src/main/resources/db/migration/V1__create_auth_tables.sql`

3 张表:
- **t_user**: id, phone(UNIQUE), password, status(NORMAL/LOCKED_TEMP/FROZEN), failed_login_count, locked_until, password_changed_at, created_at, updated_at
- **t_account**: id, user_id(UNIQUE FK), balance(DECIMAL 18,2), status, created_at, updated_at
- **t_audit_log**: id, user_id, action, resource, detail(JSON), ip, created_at; 索引: user_id, action, created_at

### Step 4: Entity 层 (3 files, 无相互依赖)

**4.1** `mbank-backend/src/main/java/com/mbank/entity/User.java`
- @Entity @Table(name="t_user"), Lombok @Data
- 内部枚举 UserStatus: NORMAL, LOCKED_TEMP, FROZEN
- status 用 @Enumerated(EnumType.STRING)
- passwordChangedAt 字段用于 token 失效检查
- @PrePersist/@PreUpdate 管理时间戳

**4.2** `mbank-backend/src/main/java/com/mbank/entity/Account.java`
- @Entity @Table(name="t_account")
- balance: BigDecimal, userId: Long (不用 JPA 关联, MVP 简化)

**4.3** `mbank-backend/src/main/java/com/mbank/entity/AuditLog.java`
- @Entity @Table(name="t_audit_log")
- userId 可为 null (注册时可能没有)

### Step 5: Repository 层 (3 files)

**5.1** `UserRepository.java` — findByPhone(), existsByPhone()
**5.2** `AccountRepository.java` — findByUserId()
**5.3** `AuditLogRepository.java` — 仅 save

### Step 6: 基础设施 - 异常与响应 (3 files)

**6.1** `mbank-backend/src/main/java/com/mbank/dto/response/ApiResponse.java`
- 泛型 ApiResponse<T>: code, message, data
- 静态工厂: success(data), success(message, data), error(code, message)

**6.2** `mbank-backend/src/main/java/com/mbank/exception/BusinessException.java`
- extends RuntimeException, 字段: code, message
- 静态工厂: badRequest(), unauthorized(), forbidden(), notFound(), conflict()

**6.3** `mbank-backend/src/main/java/com/mbank/exception/GlobalExceptionHandler.java`
- @RestControllerAdvice
- 处理: BusinessException, MethodArgumentNotValidException(400), HttpMessageNotReadableException(400), Exception(500)
- 所有异常统一返回 ApiResponse JSON

### Step 7: 安全基础设施 (4 files)

**7.1** `mbank-backend/src/main/java/com/mbank/security/JwtUtil.java`
- @Component, 注入 jwt.secret + expiration-hours
- generateToken(userId, phone) -> JWT string (HS256)
- validateToken(token) -> Claims
- getUserIdFromToken(token) -> Long

**7.2** `mbank-backend/src/main/java/com/mbank/security/JwtAuthenticationFilter.java`
- extends OncePerRequestFilter
- 从 Authorization: Bearer 提取 token
- 验证 token + 检查 passwordChangedAt (token 的 issuedAt 必须在 passwordChangedAt 之后)
- 设置 SecurityContext (principal = userId)
- 验证失败不抛异常, 仅不设认证信息

**7.3** `mbank-backend/src/main/java/com/mbank/config/SecurityConfig.java`
- @EnableWebSecurity
- /api/v1/auth/** permitAll, 其余 authenticated
- csrf 禁用, session STATELESS
- BCryptPasswordEncoder(10) bean
- 自定义 AuthenticationEntryPoint(401 JSON) + AccessDeniedHandler(403 JSON)

**7.4** `mbank-backend/src/main/java/com/mbank/config/CorsConfig.java`
- dev 环境允许 localhost, 方法 GET/POST/PUT/DELETE

### Step 8: DTO 层 (5 files)

**8.1** `RegisterRequest.java` — phone(@Pattern 11位手机), smsCode(@NotBlank), password(@Size min=8)
**8.2** `LoginRequest.java` — phone, password
**8.3** `ChangePasswordRequest.java` — oldPassword, newPassword(@Size min=8)
**8.4** `LoginResponse.java` — token
**8.5** `UserResponse.java` — id, phone(脱敏), status, createdAt

### Step 9: Service 层 (4 files)

**9.1** `AuditLogService.java` — 接口: log(action, resource, userId, detail, ip)
**9.2** `AuditLogServiceImpl.java` — @Transactional(propagation=REQUIRES_NEW), 审计不受外部事务回滚影响

**9.3** `UserService.java` — 接口: register, login, getCurrentUser, changePassword

**9.4** `UserServiceImpl.java` — 核心业务逻辑:

- **register**: 验证 SMS mock code -> 密码强度(至少2类: 大写/小写/数字) -> 手机号唯一性 -> 创建 User + Account -> 审计日志
- **login**: 查用户 -> 检查状态(FROZEN/LOCKED_TEMP 含自动解锁) -> 验密码 -> 失败: 计数+可能锁定 / 成功: 重置计数+生成JWT -> 审计日志
- **getCurrentUser**: 查用户 -> 手机号脱敏(138****8000) -> 返回
- **changePassword**: 验旧密码 -> 新密码强度校验 -> 新旧不能相同 -> 更新密码+passwordChangedAt -> 审计日志

### Step 10: Controller 层 (2 files)

**10.1** `mbank-backend/src/main/java/com/mbank/controller/AuthController.java`
- @RequestMapping("/api/v1/auth")
- POST /register -> 201, POST /login -> 200
- 从 HttpServletRequest 提取 clientIp

**10.2** `mbank-backend/src/main/java/com/mbank/controller/UserController.java`
- @RequestMapping("/api/v1/users")
- GET /me, PUT /me/password
- 从 Authentication 提取 userId

---

## 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| Token 失效 | User.passwordChangedAt 时间戳比对 | MVP 无需 Redis, 简单有效 |
| 实体关系 | 不用 JPA @OneToOne 关联 | MVP 简化, 避免懒加载复杂性 |
| 审计日志事务 | REQUIRES_NEW | 业务回滚不丢失审计记录 |
| 密码强度校验 | Service 层代码校验 | "至少2类" 规则无法用单个 @Pattern 表达 |
| 登录错误消息 | 模糊提示"手机号或密码错误" | 防止手机号枚举攻击 |
| 锁定过期 | 登录时懒检查, 无定时任务 | MVP 够用 |
| Service 返回 | ApiResponse<T> | 减少 Controller 模板代码 |

---

## 文件清单 (24 files)

```
mbank-backend/
├── pom.xml
├── src/main/java/com/mbank/
│   ├── MBankApplication.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Account.java
│   │   └── AuditLog.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── AccountRepository.java
│   │   └── AuditLogRepository.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   └── ChangePasswordRequest.java
│   │   └── response/
│   │       ├── ApiResponse.java
│   │       ├── LoginResponse.java
│   │       └── UserResponse.java
│   ├── exception/
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   ├── security/
│   │   ├── JwtUtil.java
│   │   └── JwtAuthenticationFilter.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   └── CorsConfig.java
│   ├── service/
│   │   ├── AuditLogService.java
│   │   ├── UserService.java
│   │   └── impl/
│   │       ├── AuditLogServiceImpl.java
│   │       └── UserServiceImpl.java
│   └── controller/
│       ├── AuthController.java
│       └── UserController.java
└── src/main/resources/
    ├── application.yml
    └── db/migration/
        └── V1__create_auth_tables.sql
```

---

## 验证计划

### 前置条件
1. MySQL 运行中, 已创建 mbank 数据库: `CREATE DATABASE mbank CHARACTER SET utf8mb4;`
2. 启动应用: `cd mbank-backend && mvn spring-boot:run`
3. Flyway 自动执行 V1 迁移

### 测试场景

| # | 场景 | 请求 | 期望结果 |
|---|------|------|----------|
| 1 | 注册成功 | POST /auth/register {"phone":"13800138000","smsCode":"123456","password":"Password1"} | 201, 返回脱敏用户信息 |
| 2 | 重复注册 | 同上 | 409, "手机号已注册" |
| 3 | 错误验证码 | smsCode="999999" | 400, "验证码不正确" |
| 4 | 弱密码 | password="12345678" | 400, "密码须包含...至少两种" |
| 5 | 登录成功 | POST /auth/login {"phone":"13800138000","password":"Password1"} | 200, 返回 JWT token |
| 6 | 密码错误5次锁定 | 连续5次错误密码 | 前4次401, 第5次403锁定 |
| 7 | 获取用户(已认证) | GET /users/me + Bearer token | 200, 手机号脱敏 |
| 8 | 获取用户(无token) | GET /users/me | 401 |
| 9 | 修改密码 | PUT /users/me/password {"oldPassword":"Password1","newPassword":"NewPass2"} | 200, "密码修改成功" |
| 10 | 旧 token 失效 | GET /users/me 用修改前的 token | 401 |
| 11 | 新密码登录 | POST /auth/login 用 NewPass2 | 200, 新 token |
