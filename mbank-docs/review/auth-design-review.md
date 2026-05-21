# 设计评审记录 - 认证模块 MVP

## 评审概况

| 项目 | 内容 |
|------|------|
| 评审模块 | 认证模块 MVP（注册、登录、获取用户信息、修改密码） |
| 评审日期 | 2026-05-17 |
| 评审视角 | Architect（架构师） |
| 评审方法 | 从架构设计、数据模型、API 设计、安全方案、交互流程 5 个维度逐项评审 |
| 评审文档 | architecture.md、data-model.md、api-spec.md、interaction-flow.md |
| 对标规范 | coding-standard.md、api-design.md、security-policy.md、testing-standard.md |

---

## 1. 架构设计评审

### 1.1 分层架构

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 分层是否清晰 | PASS | Controller → Service → Repository → Entity，职责边界明确 |
| 单一职责原则 | PASS | 每个包/类职责单一：controller 只做参数校验和路由，service 承担业务逻辑 |
| 依赖方向 | PASS | 上层依赖下层，无循环依赖 |
| 接口隔离 | PASS | Service 层定义接口（UserService）+ 实现（UserServiceImpl），便于 Mock 测试 |

### 1.2 模块划分

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 包结构合理性 | PASS | 9 个包各司其职，符合 Spring Boot 标准实践 |
| DTO 拆分 | PASS | request/response 分包，防止内部模型泄露 |
| 安全组件独立 | PASS | security/ 独立于 config/，职责清晰 |
| 异常处理独立 | PASS | exception/ 包含 BusinessException + GlobalExceptionHandler |

### 1.3 技术选型

| 技术 | 评估 | 说明 |
|------|------|------|
| Spring Boot 3.2.5 | PASS | 当前 LTS 版本，社区活跃，与 Java 17 完美适配 |
| Java 17 | PASS | LTS 版本，支持 Records、Sealed Classes 等现代特性 |
| Spring Data JPA | PASS | 天然防 SQL 注入，适合 CRUD 为主的认证模块 |
| jjwt 0.12.5 | PASS | 成熟稳定的 JWT 库，API 设计合理 |
| Flyway | PASS | 数据库版本管理最佳实践 |
| Lombok | PASS | 减少样板代码，团队熟悉度高 |

### 1.4 扩展性评估

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 新模块接入 | PASS | 后续银行卡、交易模块可直接新增包，不影响现有结构 |
| SMS 服务扩展 | ISSUE | 当前 SMS 验证逻辑内嵌在 UserServiceImpl 中，无独立接口抽象 |
| 认证方式扩展 | PASS | Spring Security Filter 链机制支持后续增加 OAuth2、生物识别等 |
| 缓存层接入 | PASS | Service 层可透明引入缓存，不影响 Controller/Repository |

### 1.5 架构问题清单

| 编号 | 级别 | 问题 | 建议 |
|------|------|------|------|
| ARCH-01 | SUGGESTION | SMS 验证逻辑无接口抽象，直接硬编码在 Service 内。违反依赖倒置原则，后续替换真实网关需修改 Service 代码。 | 提取 `SmsService` 接口 + `MockSmsServiceImpl`，通过 Spring Profile 切换实现。 |
| ARCH-02 | INFO | 当前无 application 层（Application Service），Service 直接承担编排职责。对 MVP 4 个接口而言可接受。 | 后续模块增多时考虑引入 Application Service 层做跨 Service 编排。 |

---

## 2. 数据模型评审

### 2.1 表结构

#### t_user

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 主键策略 | PASS | BIGINT AUTO_INCREMENT，适合单机/低并发 MVP |
| 手机号字段 | PASS | VARCHAR(20) + UNIQUE，支持国际号码预留 |
| 密码存储 | PASS | VARCHAR(100)，BCrypt 输出固定 60 字符，100 字符有余量 |
| 状态字段 | PASS | VARCHAR(20) + DEFAULT 'NORMAL'，枚举值明确 |
| 时间字段 | PASS | DATETIME + DEFAULT CURRENT_TIMESTAMP，自动管理 |
| 锁定机制 | PASS | failed_login_count + locked_until 组合实现懒检查 |
| Token 失效 | PASS | password_changed_at 字段，简洁有效 |
| 缺失字段 | ISSUE | 缺少 `mobile_verified_at`（需求 §8.0 要求） |

#### t_account

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 一对一关系 | PASS | user_id UNIQUE FK，确保一用户一账户 |
| 余额精度 | PASS | DECIMAL(18,2)，满足金融精度要求（最大支持千万亿级） |
| 并发安全预留 | ISSUE | 缺少 `version` 字段（乐观锁），后续交易模块需要 |

#### t_audit_log

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 用户ID可空 | PASS | 注册等匿名操作 user_id 可为 null，设计合理 |
| detail 字段 | PASS | VARCHAR(500) JSON 格式，灵活存储不同操作详情 |
| IP 字段 | PASS | VARCHAR(50) 支持 IPv6 |
| 时间字段 | PASS | created_at 不可更新，保证审计不可篡改 |

### 2.2 索引设计

| 索引 | 评估 | 说明 |
|------|------|------|
| uk_user_phone | PASS | 注册查重 + 登录查询核心索引 |
| uk_account_user_id | PASS | 用户-账户一对一保障 |
| idx_audit_user_id | PASS | 按用户查审计记录 |
| idx_audit_action | PASS | 按操作类型查询 |
| idx_audit_created_at | PASS | 按时间范围查询 |
| 缺失索引 | INFO | t_user 无 status 索引，但 MVP 阶段不需要按状态批量查询 |

### 2.3 状态机

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 状态定义完整 | PASS | NORMAL / LOCKED_TEMP / FROZEN 覆盖需求 §7.1 |
| 转移规则明确 | PASS | 每条转移有触发条件和操作 |
| 与需求一致性 | PASS | 与需求文档 §7.1 状态流转图一致 |
| 状态持久化 | PASS | status VARCHAR(20) 枚举字符串，可读性好 |

### 2.4 数据模型问题清单

| 编号 | 级别 | 问题 | 建议 |
|------|------|------|------|
| DM-01 | MINOR | t_user 缺少 `mobile_verified_at` 字段。需求 §8.0 要求注册成功后写入此时间戳。 | 增加 `mobile_verified_at DATETIME NULL`，注册成功时写入。 |
| DM-02 | SUGGESTION | t_account 缺少 `version` 字段用于乐观锁。当前 MVP 无余额变动操作，但后续交易模块需要。 | 建议在初始建表时预留 `version INT NOT NULL DEFAULT 0`，避免后续 ALTER TABLE。 |
| DM-03 | INFO | 审计日志 detail 字段使用 VARCHAR(500)，某些复杂操作（如包含完整请求体的交易审计）可能不够。 | MVP 阶段 500 字符充足。后续扩展为 TEXT 或迁移至独立日志存储。 |

---

## 3. API 设计评审

### 3.1 RESTful 规范符合度

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 基础路径 /api/v1 | PASS | 符合 api-design.md 规范 |
| 资源命名复数 | PASS | /users/me、/auth/register |
| HTTP Method 语义 | PASS | POST 创建、GET 查询、PUT 修改 |
| URL 设计 | PASS | 与 api-design.md 路由表完全一致 |

### 3.2 请求设计

| 接口 | 检查项 | 评估 | 说明 |
|------|--------|------|------|
| register | 字段完整 | PASS | phone + smsCode + password |
| register | 校验注解 | PASS | 正则、长度约束明确 |
| login | 字段完整 | PASS | phone + password |
| users/me | 无请求体 | PASS | 通过 JWT 识别用户 |
| me/password | 字段完整 | PASS | oldPassword + newPassword |

### 3.3 响应设计

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 统一包装 ApiResponse | PASS | code + message + data 三字段结构 |
| 成功状态码 | PASS | 注册 201、其余 200 |
| 数据脱敏 | PASS | 响应中手机号脱敏为 138****8000 |
| 错误响应 | PASS | 每个接口列出所有错误场景和对应状态码 |

### 3.4 HTTP 状态码使用

| 状态码 | 使用场景 | 评估 | 说明 |
|--------|----------|------|------|
| 200 | 登录/查询/改密成功 | PASS | |
| 201 | 注册成功 | PASS | |
| 400 | 参数校验失败 | PASS | |
| 401 | 未认证/密码错误 | PASS | |
| 403 | 账户锁定/冻结 | PASS | |
| 404 | 用户不存在 | PASS | |
| 409 | 手机号已注册 | PASS | |

### 3.5 API 设计问题清单

| 编号 | 级别 | 问题 | 建议 |
|------|------|------|------|
| API-01 | MINOR | 注册成功返回 HTTP 201 但 body.code = 200。两层语义可能造成前端判断混淆。 | 明确约定：HTTP status 表示传输层语义，body.code 为业务码恒为 200 表示成功。在 api-spec.md 通用约定中补充说明。 |
| API-02 | INFO | 登录失败"还剩N次机会"信息嵌入 message 字符串中，前端需解析字符串获取数字。 | 可接受方案。如需精确控制前端展示，可在 data 中增加 `remainingAttempts` 字段，但 MVP 阶段 message 方案足够。 |

---

## 4. 安全设计评审

### 4.1 JWT 认证方案

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 算法选择 HS256 | PASS | 对称算法，单服务部署够用；多服务时需切换 RS256 |
| 密钥长度 >= 256 位 | PASS | 设计文档明确要求 |
| Token 有效期 24h | PASS | 符合 security-policy.md |
| Token 载荷最小化 | PASS | 仅 userId + phone + issuedAt + exp，无敏感信息 |
| Token 传递方式 | PASS | Authorization: Bearer，标准实践 |
| Token 失效机制 | PASS | passwordChangedAt 比对，无需 Redis，MVP 适用 |

### 4.2 密码安全

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 存储方式 BCrypt | PASS | 强度因子 10，行业标准 |
| 密码强度规则 | PASS | >= 8位 + 至少2种字符类型 |
| 明文不落盘 | PASS | 仅在内存中处理，持久化为哈希 |
| 旧密码验证 | PASS | 修改密码前必须验证旧密码 |
| 新旧密码不同 | PASS | 设计中明确校验 |

### 4.3 鉴权规则

| 检查项 | 评估 | 说明 |
|--------|------|------|
| /auth/** permitAll | PASS | 注册和登录无需认证 |
| /users/** authenticated | PASS | 需要 JWT 且只能访问自己的数据 |
| 数据越权防护 | PASS | 通过 JWT 中的 userId 限制访问范围 |

### 4.4 审计日志

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 覆盖操作 | PASS | REGISTER / LOGIN / LOGIN_FAILED / ACCOUNT_LOCKED / CHANGE_PASSWORD |
| 独立事务 | PASS | REQUIRES_NEW 保证业务回滚不丢失审计 |
| 敏感数据脱敏 | PASS | detail 中手机号已脱敏 |
| IP 记录 | PASS | 设计中明确记录客户端 IP |

### 4.5 安全设计问题清单

| 编号 | 级别 | 问题 | 建议 |
|------|------|------|------|
| SEC-01 | MAJOR | LOCKED_TEMP/FROZEN 状态用户已签发的 Token 仍然有效。需求 §7.2 要求"用户进入 LOCKED_TEMP 时所有 VALID Token 立即 REVOKED"。当前 Token 失效仅依赖 passwordChangedAt，锁定操作不触发 Token 失效。 | **方案A（推荐）**：JwtAuthenticationFilter 中查询用户状态，LOCKED_TEMP/FROZEN 直接返回 401。额外增加一次 DB 查询但逻辑简单。**方案B**：锁定时同步更新 passwordChangedAt，但语义被滥用，不推荐。 |
| SEC-02 | INFO | JWT 使用 HS256 对称算法，如果未来拆分微服务需要共享密钥或切换 RS256。 | MVP 单体应用无问题。记录为技术债，后续架构演进时评估。 |

---

## 5. 交互流程评审

### 5.1 流程完整性

| 流程 | Happy Path | 异常路径 | 事务边界 | 评估 |
|------|-----------|----------|----------|------|
| 注册 | 校验→查重→建User→建Account→审计→响应 | SMS错误/密码弱/手机号重复 | User+Account 同一事务 | PASS |
| 登录 | 查用户→检状态→验密码→重置计数→生成Token→审计 | 用户不存在/FROZEN/LOCKED/密码错 | 状态更新同事务 | PASS |
| 获取用户信息 | Filter验Token→查用户→脱敏→响应 | Token无效/已过期/已改密 | 只读无事务 | PASS |
| 修改密码 | 验旧密码→验新密码强度→更新密码→更新时间→审计 | 旧密码错/强度不足/新旧相同 | 密码更新+审计在事务中 | PASS |

### 5.2 时序图质量

| 检查项 | 评估 | 说明 |
|--------|------|------|
| 参与者标注清晰 | PASS | Client, Controller, Service, Repository, DB 各角色明确 |
| 消息命名规范 | PASS | 方法名 + 参数，可直接对应代码实现 |
| 异常分支覆盖 | PASS | 使用 alt 块覆盖所有错误场景 |
| 响应格式标注 | PASS | 标注 HTTP 状态码和响应体结构 |

### 5.3 事务边界

| 流程 | 事务范围 | 评估 | 说明 |
|------|----------|------|------|
| 注册 | User 创建 + Account 创建 | PASS | 同一事务，失败全部回滚 |
| 登录（锁定） | 用户状态更新 + 审计日志 | ISSUE | 审计日志使用 REQUIRES_NEW，但状态更新如果回滚，审计日志仍会写入。语义上合理（审计应记录尝试而非结果）。 |
| 修改密码 | 密码更新 + passwordChangedAt | PASS | 同一事务 |

### 5.4 交互流程问题清单

| 编号 | 级别 | 问题 | 建议 |
|------|------|------|------|
| FLOW-01 | MINOR | 获取用户信息流程中，JwtAuthenticationFilter 查询 DB 获取 user 后，UserController 再次查询 DB 获取同一用户。存在重复查询。 | 方案A：Filter 验证后将 User 对象存入 SecurityContext/Request Attribute，Controller 直接取用。方案B：仅存 userId 到 SecurityContext（当前设计），Service 层查询。推荐方案B（职责清晰，虽有额外查询但 MVP 可接受）。 |
| FLOW-02 | INFO | 注册流程中 SMS 验证码校验位于 Service 第一步，在 DB 查重之前。如果 SMS 先做，大量恶意请求都会消耗 SMS 验证逻辑。 | MVP Mock 模式无性能影响。真实上线后建议调整顺序：先做简单的格式校验和 DB 查重（轻量级），再做 SMS 验证（重量级外部调用）。 |

---

## 6. 综合评估

### 设计质量评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | 9/10 | 分层清晰、技术选型合理、扩展性良好 |
| 数据模型 | 8/10 | 表结构合理，少量字段遗漏 |
| API 设计 | 9/10 | 严格遵循 RESTful 规范和项目标准 |
| 安全设计 | 8/10 | JWT + BCrypt + 审计完备，Token 失效有漏洞 |
| 交互流程 | 9/10 | 时序图详细完整，异常覆盖全面 |
| **总体** | **8.5/10** | |

---

## 7. 评审结论

### 评审结果：有条件通过

### 必须修改项（MUST FIX）

| 编号 | 来源 | 问题 | 修改方案 |
|------|------|------|----------|
| MF-01 | SEC-01 | LOCKED_TEMP/FROZEN 用户已签发 Token 仍可用，违反 §7.2 | JwtAuthenticationFilter 中增加用户状态检查：状态为 LOCKED_TEMP 或 FROZEN 时返回 401 |

### 建议修改项（SHOULD FIX）

| 编号 | 来源 | 问题 | 修改方案 |
|------|------|------|----------|
| SF-01 | ARCH-01 | SMS 验证无接口抽象 | 提取 `SmsService` 接口 + `MockSmsServiceImpl`，代码生成时直接落实 |
| SF-02 | DM-01 | t_user 缺少 mobile_verified_at 字段 | Flyway 迁移脚本中加入该字段 |
| SF-03 | DM-02 | t_account 缺少 version 字段（乐观锁预留） | 建表时预留 `version INT NOT NULL DEFAULT 0` |
| SF-04 | API-01 | HTTP 201 vs body.code 200 关系未声明 | api-spec.md 通用约定中补充说明 |

### 无需修改项（ACCEPTED AS-IS）

| 编号 | 来源 | 说明 |
|------|------|------|
| ACC-01 | FLOW-01 | Filter 和 Service 重复查询用户：MVP 阶段可接受，性能不是瓶颈 |
| ACC-02 | SEC-02 | HS256 对称算法：单体应用适用，微服务时再切换 |
| ACC-03 | FLOW-02 | SMS 校验顺序：Mock 模式无性能问题 |
| ACC-04 | DM-03 | detail 字段 VARCHAR(500)：MVP 阶段足够 |
| ACC-05 | ARCH-02 | 无 Application Service 层：MVP 4 接口无需额外分层 |
| ACC-06 | API-02 | 剩余次数嵌入 message：前端可处理 |

---

## 8. 代码生成指引

基于评审结论，Phase 2（/generate-api）执行时需落实以下调整：

1. **JwtAuthenticationFilter** 中增加用户状态检查（MF-01）
2. 新增 `SmsService` 接口 + `MockSmsServiceImpl`（SF-01）
3. t_user 增加 `mobile_verified_at DATETIME NULL`（SF-02）
4. t_account 增加 `version INT NOT NULL DEFAULT 0`（SF-03）
5. api-spec.md 补充 HTTP status 与 body.code 关系说明（SF-04）

---

*评审完成*
