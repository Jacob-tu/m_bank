# 交互流程设计 - 认证模块 MVP

## 文档信息

| 项目 | 内容 |
|------|------|
| 项目名称 | M-Bank 简易手机银行系统 |
| 模块名称 | 认证模块 |
| 版本 | v1.0 |
| 编写日期 | 2026-05-17 |
| 编写方式 | AI 原生（architect Agent） |

## 1. 用户注册流程

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant US as UserServiceImpl
    participant UR as UserRepository
    participant AR as AccountRepository
    participant AL as AuditLogService
    participant DB as MySQL

    C->>AC: POST /api/v1/auth/register<br/>{phone, smsCode, password}
    AC->>AC: @Valid 参数校验
    AC->>US: register(request, clientIp)
    
    US->>US: 验证SMS验证码(Mock: 123456)
    US->>US: 验证密码强度(至少2类)
    US->>UR: existsByPhone(phone)
    UR->>DB: SELECT count(*) FROM t_user
    DB-->>UR: result
    UR-->>US: true/false
    
    alt 手机号已注册
        US-->>AC: throw BusinessException(409, "手机号已注册")
        AC-->>C: HTTP 409 {"code":409,"message":"手机号已注册"}
    end
    
    US->>UR: save(user)
    UR->>DB: INSERT INTO t_user
    DB-->>UR: user(id=1)
    
    US->>AR: save(account)
    AR->>DB: INSERT INTO t_account
    DB-->>AR: account
    
    US->>AL: log(REGISTER, USER, userId, detail, ip)
    AL->>DB: INSERT INTO t_audit_log
    
    US-->>AC: ApiResponse.success("注册成功", userResponse)
    AC-->>C: HTTP 201 {"code":200,"message":"注册成功","data":{...}}
```

## 2. 用户登录流程

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant US as UserServiceImpl
    participant UR as UserRepository
    participant JWT as JwtUtil
    participant AL as AuditLogService
    participant DB as MySQL

    C->>AC: POST /api/v1/auth/login<br/>{phone, password}
    AC->>AC: @Valid 参数校验
    AC->>US: login(request, clientIp)
    
    US->>UR: findByPhone(phone)
    UR->>DB: SELECT * FROM t_user WHERE phone=?
    DB-->>UR: user / null
    
    alt 用户不存在
        US->>AL: log(LOGIN_FAILED, ...)
        US-->>AC: throw BusinessException(401, "手机号或密码错误")
        AC-->>C: HTTP 401
    end
    
    US->>US: 检查用户状态
    
    alt status = FROZEN
        US-->>AC: throw BusinessException(403, "账户已冻结")
    end
    
    alt status = LOCKED_TEMP
        US->>US: 检查 lockedUntil 是否过期
        alt 仍在锁定期
            US-->>AC: throw BusinessException(403, "账户已临时锁定")
        else 锁定已过期
            US->>US: 自动解锁(status=NORMAL, count=0)
        end
    end
    
    US->>US: BCrypt.matches(password, hash)
    
    alt 密码错误
        US->>US: failedLoginCount++
        alt count >= 5 (阈值)
            US->>US: status = LOCKED_TEMP, lockedUntil = now+15min
            US->>UR: save(user)
            US->>AL: log(ACCOUNT_LOCKED, ...)
            US-->>AC: throw BusinessException(403, "账户已锁定15分钟")
        else count < 5
            US->>UR: save(user)
            US->>AL: log(LOGIN_FAILED, ...)
            US-->>AC: throw BusinessException(401, "手机号或密码错误，还剩N次机会")
        end
    end
    
    US->>US: 密码正确 -> 重置 failedLoginCount=0
    US->>UR: save(user)
    US->>JWT: generateToken(userId, phone)
    JWT-->>US: token
    US->>AL: log(LOGIN, USER, userId, detail, ip)
    US-->>AC: ApiResponse.success(LoginResponse(token))
    AC-->>C: HTTP 200 {"code":200,"data":{"token":"eyJ..."}}
```

## 3. 获取当前用户信息流程

```mermaid
sequenceDiagram
    participant C as Client
    participant JF as JwtAuthenticationFilter
    participant JWT as JwtUtil
    participant UR as UserRepository
    participant UC as UserController
    participant US as UserServiceImpl
    participant DB as MySQL

    C->>JF: GET /api/v1/users/me<br/>Authorization: Bearer <token>
    JF->>JF: 提取 Bearer token
    JF->>JWT: validateToken(token)
    JWT-->>JF: Claims(userId, phone, issuedAt)
    
    JF->>UR: findById(userId)
    UR->>DB: SELECT * FROM t_user
    DB-->>UR: user
    
    JF->>JF: 检查 issuedAt > passwordChangedAt?
    
    alt Token 已失效(改密后签发的旧token)
        JF-->>C: 不设SecurityContext, 继续filter链
        Note over C: Spring Security 返回 401
    end
    
    JF->>JF: 设置 SecurityContext(userId)
    JF->>UC: 继续请求链
    
    UC->>US: getCurrentUser(userId)
    US->>UR: findById(userId)
    UR->>DB: SELECT * FROM t_user
    DB-->>UR: user
    US->>US: 手机号脱敏: 138****8000
    US-->>UC: ApiResponse.success(UserResponse)
    UC-->>C: HTTP 200 {"code":200,"data":{"id":1,"phone":"138****8000",...}}
```

## 4. 修改密码流程

```mermaid
sequenceDiagram
    participant C as Client
    participant JF as JwtAuthenticationFilter
    participant UC as UserController
    participant US as UserServiceImpl
    participant UR as UserRepository
    participant AL as AuditLogService
    participant DB as MySQL

    C->>JF: PUT /api/v1/users/me/password<br/>Authorization: Bearer <token><br/>{oldPassword, newPassword}
    JF->>JF: JWT 验证通过, 设置 SecurityContext
    JF->>UC: 继续请求链
    
    UC->>US: changePassword(userId, request, clientIp)
    US->>UR: findById(userId)
    UR->>DB: SELECT * FROM t_user
    DB-->>UR: user
    
    US->>US: BCrypt.matches(oldPassword, hash)
    alt 旧密码不正确
        US-->>UC: throw BusinessException(400, "旧密码不正确")
        UC-->>C: HTTP 400
    end
    
    US->>US: 验证新密码强度
    US->>US: 验证新旧密码不同
    
    US->>US: password = BCrypt.encode(newPassword)
    US->>US: passwordChangedAt = now()
    US->>UR: save(user)
    UR->>DB: UPDATE t_user SET password=?, password_changed_at=?
    
    US->>AL: log(CHANGE_PASSWORD, USER, userId, detail, ip)
    AL->>DB: INSERT INTO t_audit_log
    
    US-->>UC: ApiResponse.success("密码修改成功，请重新登录", null)
    UC-->>C: HTTP 200 {"code":200,"message":"密码修改成功，请重新登录"}
    
    Note over C: 之后使用旧 Token 请求将被 JwtAuthenticationFilter 拒绝<br/>(issuedAt < passwordChangedAt)
```

## 5. 用户状态流转图

```mermaid
stateDiagram-v2
    [*] --> NORMAL : 注册成功
    NORMAL --> LOCKED_TEMP : 登录密码连续错误达到阈值(5次)
    LOCKED_TEMP --> NORMAL : 锁定时间到(15分钟)/人工解锁
    NORMAL --> FROZEN : 风控/运营冻结
    LOCKED_TEMP --> FROZEN : 风控升级冻结
    FROZEN --> NORMAL : 人工审核通过
```
