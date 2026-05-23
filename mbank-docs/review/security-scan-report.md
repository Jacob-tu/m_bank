# M-Bank 后端安全扫描报告

**扫描时间**: 2026-05-23  
**扫描范围**: mbank-backend 认证模块（Phase 2 生成代码）  
**扫描维度**: SQL 注入、XSS、认证鉴权、敏感数据、CORS、业务安全

---

## 总体评级: 🟢 良好（1 中危 + 3 低危 + 若干改进建议）

| 维度 | 评级 | 发现问题 |
|------|------|----------|
| SQL 注入防护 | ✅ 通过 | 无问题 |
| XSS 防护 | ✅ 通过 | 无问题 |
| 认证鉴权 | ✅ 通过 | 无问题 |
| 敏感数据 | ⚠️ 关注 | 2 个低危 |
| CORS 配置 | ✅ 通过 | 无问题 |
| 业务安全 | ⚠️ 关注 | 1 中危 + 1 低危 |

---

## 1. SQL 注入扫描 ✅

**结论**: 无风险

**检查点**:
- `UserRepository` / `AccountRepository` / `AuditLogRepository` 全部使用 Spring Data JPA 派生查询（`findByPhone`、`existsByPhone`）
- 无 `@Query` 原生 SQL，无字符串拼接 SQL
- 所有查询参数通过 JPA 参数绑定传递

**代码证据**:
```java
// UserRepository.java - 派生查询，天然防注入
Optional<User> findByPhone(String phone);
boolean existsByPhone(String phone);
```

---

## 2. XSS 防护扫描 ✅

**结论**: 无风险

**检查点**:
- 后端 REST API 返回 JSON，无 HTML 渲染
- 响应格式 `ApiResponse<T>` 为结构化 JSON，不拼接 HTML
- 异常消息（`GlobalExceptionHandler`）返回固定字符串，不回显用户输入
- 审计日志 `detail` 字段入库时已做手机号脱敏处理，不做输出展示

**低危提示**: 生产环境建议在响应头添加 `X-Content-Type-Options: nosniff` 和 `X-Frame-Options: DENY`（当前未配置 Security Headers）。

---

## 3. 认证鉴权扫描 ✅

**结论**: 无风险，MF-01 修复已生效

**检查点**:

### 3.1 JWT 实现
- 算法：HS256，符合安全策略
- 密钥：`Keys.hmacShaKeyFor(Base64.decode(secret))` — 标准 256 位密钥
- 有效期：24 小时（`expiration-hours: 24`）
- Token 结构：subject=userId + claim.phone + issuedAt + expiration

### 3.2 MF-01 修复验证
```java
// JwtAuthenticationFilter.java
// ① 用户状态检查
if ("FROZEN".equals(user.getStatus()) || "LOCKED_TEMP".equals(user.getStatus())) {
    sendUnauthorized(response, "请重新登录");
    return;
}
// ② 密码修改时间检查（使旧 Token 失效）
if (user.getPasswordChangedAt() != null) {
    LocalDateTime tokenIssuedAt = claims.getIssuedAt()
            .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    if (tokenIssuedAt.isBefore(user.getPasswordChangedAt())) {
        sendUnauthorized(response, "请重新登录");
        return;
    }
}
```
两项检查均已实现，密码修改后旧 Token 自动失效。

### 3.3 路由鉴权
```java
// SecurityConfig.java
.requestMatchers("/api/v1/auth/**").permitAll()  // 注册/登录不需认证
.anyRequest().authenticated()                     // 其他均需认证
```
规则符合安全策略文档。

### 3.4 BCrypt 强度
```java
new BCryptPasswordEncoder(10)  // 强度因子 10，符合策略要求（>= 10）
```

---

## 4. 敏感数据扫描 ⚠️

### 4.1 [低危] application.yml 包含明文密码和默认密钥

**文件**: `src/main/resources/application.yml`

```yaml
datasource:
  password: password          # 明文数据库密码
jwt:
  secret: bXliYW5rLXNlY3...  # 默认 JWT 密钥（注释已说明需替换）
sms:
  valid-code: "123456"        # 明文 SMS 验证码（Mock 用）
```

**风险**: 开发配置提交 Git 后泄露生产凭证（若直接复用）。

**修复建议**:
```yaml
# 生产环境使用环境变量覆盖
datasource:
  password: ${DB_PASSWORD}
jwt:
  secret: ${JWT_SECRET}
```
或使用 Spring Cloud Config / Vault 管理机密。

### 4.2 [低危] 手机号在审计日志中的脱敏不一致

**文件**: `UserServiceImpl.java`

注册、登录失败时记录的 detail 字段使用了手机号脱敏（`maskPhone`），但登录成功日志只记录 userId，保持一致，无问题。

检查通过，但 `AuditLog.detail` 字段长度 `VARCHAR(500)` 在记录 JSON detail 时需注意不要超长。

### 4.3 UserResponse 手机号脱敏 ✅

```java
// UserResponse.from(user) 自动脱敏 138****8000
return phone.substring(0, 3) + "****" + phone.substring(7);
```
实现正确，接口不会返回完整手机号。

---

## 5. CORS 配置扫描 ✅

**结论**: 配置符合安全策略

```java
// CorsConfig.java
config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
config.setAllowCredentials(false);  // JWT 模式正确，不需要 Cookie
```

**符合策略**:
- 仅允许 localhost（开发环境）
- `AllowCredentials = false`（JWT 无 Cookie 需求）
- 未使用 `*` 通配符

**生产部署提示**: 需将 `localhost` 替换为实际生产域名，建议通过 `@Value("${mbank.cors.allowed-origins}")` 外部配置化。

---

## 6. 业务安全扫描 ⚠️

### 6.1 [中危] X-Forwarded-For IP 未做验证，存在 IP 伪造风险

**文件**: `AuthController.java`（第 64-70 行）

```java
private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
        return xForwardedFor.split(",")[0].trim();  // 直接信任请求头
    }
    return request.getRemoteAddr();
}
```

**风险**: 攻击者可以自行设置 `X-Forwarded-For: 127.0.0.1` 来伪造 IP，使审计日志中的 IP 记录失真。这会影响安全审计追踪的可靠性。

**修复建议**: 仅在应用前置 Nginx/负载均衡器时信任 X-Forwarded-For，且应只取最后一跳的可信 IP：
```java
// 方案1：通过配置控制是否信任代理 IP（推荐）
// 在 application.yml 中配置 server.forward-headers-strategy=native
// Spring Boot 将自动处理代理头，使 request.getRemoteAddr() 返回真实 IP

// 方案2：仅信任来自内网代理的 X-Forwarded-For
private String getClientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    // 只有当请求来自内网代理时，才信任 X-Forwarded-For
    if (isTrustedProxy(remoteAddr)) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
    }
    return remoteAddr;
}
```

### 6.2 [低危] 登录失败信息泄露剩余次数

**文件**: `UserServiceImpl.java`（第 169 行）

```java
throw new BusinessException(401, String.format("手机号或密码错误，还剩%d次机会", remaining));
```

**风险**: 告知攻击者剩余尝试次数，协助其精确控制爆破节奏（在锁定前停止，规避检测）。

**修复建议**: 统一返回模糊错误信息，不暴露剩余次数：
```java
throw new BusinessException(401, "手机号或密码错误");
```

### 6.3 账户锁定机制 ✅

- 5次错误后自动锁定 15 分钟（LOCKED_TEMP）
- 锁定过期后自动解锁（懒检查）
- FROZEN 状态永久拒绝登录
- 登录成功后重置失败计数

### 6.4 密码强度校验 ✅

- 长度 8-20 位（`@Size`）
- 至少包含大写、小写、数字中的两种（`validatePasswordStrength`）
- 修改密码时校验旧密码 + 新旧不同

### 6.5 注册竞态条件（低风险）

注册流程：先 `existsByPhone` 检查，再 `save`。若并发注册相同手机号，`existsByPhone` 可能同时返回 false，导致两条记录尝试插入。

但 `t_user.phone` 字段有 `UNIQUE KEY` 约束，数据库层面会拦截第二条，抛出 `DataIntegrityViolationException`（全局异常处理器会返回 500）。

**修复建议**: 在 `GlobalExceptionHandler` 中捕获 `DataIntegrityViolationException`，识别手机号唯一约束冲突并返回 409：
```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
    if (e.getMessage() != null && e.getMessage().contains("phone")) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, "手机号已注册"));
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "服务器内部错误"));
}
```

---

## 7. 汇总与优先级

| 编号 | 严重等级 | 问题描述 | 文件 | 状态 |
|------|---------|----------|------|------|
| SEC-01 | 中危 ⚠️ | X-Forwarded-For 未验证，可伪造审计 IP | AuthController.java:64 | 待修复 |
| SEC-02 | 低危 ℹ️ | 登录失败暴露剩余次数 | UserServiceImpl.java:169 | 建议修复 |
| SEC-03 | 低危 ℹ️ | application.yml 明文密码/默认密钥 | application.yml | 生产前必须修复 |
| SEC-04 | 低危 ℹ️ | 并发注册异常未友好处理 | GlobalExceptionHandler.java | 建议修复 |
| SEC-05 | 建议 💡 | 缺少安全响应头（nosniff/X-Frame-Options） | SecurityConfig.java | 生产建议添加 |
| SEC-06 | 建议 💡 | CORS 来源未外部配置化 | CorsConfig.java | 生产前配置 |

### 生产上线前必须修复
1. **SEC-03**: 使用环境变量替换 application.yml 中的明文密码和默认密钥
2. **SEC-01**: 添加代理 IP 可信度验证（或配置 `server.forward-headers-strategy=native`）

### 建议在测试阶段修复
3. **SEC-02**: 移除登录失败消息中的剩余次数提示
4. **SEC-04**: 捕获 `DataIntegrityViolationException` 返回 409

### 生产优化项
5. **SEC-05**: SecurityConfig 添加安全响应头
6. **SEC-06**: CORS 来源通过配置文件管理

---

## 8. 合规性验证

对照 `security-policy.md` 文档逐项检查：

| 策略条目 | 实现状态 |
|---------|---------|
| JWT HS256，密钥 >= 256 位 | ✅ 已实现 |
| Access Token 有效期 24 小时 | ✅ 已实现 |
| BCrypt 强度因子 >= 10 | ✅ 已实现（factor=10） |
| 密码长度 >= 8，包含两种字符类型 | ✅ 已实现 |
| 密码修改验证旧密码 | ✅ 已实现 |
| /api/v1/auth/** 允许匿名 | ✅ 已实现 |
| 手机号脱敏展示 | ✅ 已实现（138****8000） |
| 禁止字符串拼接 SQL | ✅ 已实现（JPA 派生查询） |
| 登录记录审计日志 | ✅ 已实现 |
| 注册记录审计日志 | ✅ 已实现 |
| 密码修改记录审计日志 | ✅ 已实现 |
| CORS 允许 localhost，Credentials=false | ✅ 已实现 |
| 余额使用 DECIMAL(18,2) | ✅ 已实现 |
| Account 乐观锁（SF-03） | ✅ 已实现（@Version） |
| SMS 解耦接口（SF-01） | ✅ 已实现 |
| mobile_verified_at（SF-02） | ✅ 已实现 |
