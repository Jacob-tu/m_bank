# API 接口规范 - 认证模块 MVP

## 文档信息

| 项目 | 内容 |
|------|------|
| 项目名称 | M-Bank 简易手机银行系统 |
| API 版本 | v1 |
| 基础路径 | /api/v1 |
| 编写日期 | 2026-05-17 |
| 编写方式 | AI 原生（architect Agent） |

## 通用约定

### 认证方式

- Bearer Token（JWT）
- Header: `Authorization: Bearer <token>`

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 错误响应格式

```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

### HTTP 状态码

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 | 成功 | GET、PUT 请求成功 |
| 201 | 已创建 | POST 创建资源成功 |
| 400 | 请求错误 | 参数校验失败 |
| 401 | 未认证 | Token 缺失或过期 |
| 403 | 禁止访问 | 账户状态不允许该操作 |
| 409 | 冲突 | 资源已存在（手机号重复注册） |
| 500 | 服务器错误 | 未预期的服务端异常 |

---

## 1. 认证模块

### 1.1 用户注册

**POST** `/api/v1/auth/register`

认证：否

#### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| phone | String | 是 | 11位中国大陆手机号，正则 `^1[3-9]\d{9}$` | 手机号 |
| smsCode | String | 是 | 6位数字 | 短信验证码（Mock 模式固定为 123456） |
| password | String | 是 | 8-20位，包含大写字母、小写字母、数字中的至少两种 | 登录密码 |

#### 请求示例

```json
{
  "phone": "13800138000",
  "smsCode": "123456",
  "password": "Password1"
}
```

#### 成功响应 (HTTP 201)

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 1,
    "phone": "138****8000",
    "status": "NORMAL",
    "createdAt": "2026-05-17T10:00:00"
  }
}
```

#### 错误响应

| HTTP 状态码 | code | 场景 | message |
|------------|------|------|---------|
| 400 | 400 | 手机号格式不正确 | 手机号格式不正确 |
| 400 | 400 | 验证码不正确 | 验证码不正确 |
| 400 | 400 | 密码强度不足 | 密码须包含大写字母、小写字母、数字中的至少两种 |
| 400 | 400 | 验证码为空 | 验证码不能为空 |
| 409 | 409 | 手机号已注册 | 手机号已注册 |

#### 业务规则

1. 验证 SMS 验证码（Mock 模式下接受固定码 123456）
2. 校验密码强度：>= 8 位，包含大写字母、小写字母、数字中的至少两种
3. 校验手机号唯一性
4. 创建 User（status=NORMAL）和 Account（balance=0）在同一事务中
5. 密码使用 BCrypt（强度因子 10）加密存储
6. 记录审计日志：action=REGISTER

---

### 1.2 用户登录

**POST** `/api/v1/auth/login`

认证：否

#### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| phone | String | 是 | 11位中国大陆手机号 | 手机号 |
| password | String | 是 | - | 登录密码 |

#### 请求示例

```json
{
  "phone": "13800138000",
  "password": "Password1"
}
```

#### 成功响应 (HTTP 200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

#### 错误响应

| HTTP 状态码 | code | 场景 | message |
|------------|------|------|---------|
| 401 | 401 | 手机号未注册或密码错误 | 手机号或密码错误 |
| 401 | 401 | 密码错误（含剩余次数） | 手机号或密码错误，还剩N次机会 |
| 403 | 403 | 账户临时锁定 | 密码连续错误5次，账户已锁定15分钟 |
| 403 | 403 | 账户已冻结 | 账户已冻结，请联系客服 |

#### 业务规则

1. 根据手机号查找用户，未找到返回模糊错误（防止手机号枚举）
2. 检查用户状态：
   - FROZEN -> 拒绝登录
   - LOCKED_TEMP -> 检查锁定是否过期，过期则自动解锁
3. 验证密码：
   - 错误：递增 failedLoginCount，达到阈值（5次）则锁定 15 分钟
   - 正确：重置 failedLoginCount=0，生成 JWT Token
4. JWT Token 载荷：userId（subject）、phone、issuedAt、expiration（24小时）
5. 记录审计日志：action=LOGIN（成功）或 LOGIN_FAILED（失败）

---

## 2. 用户模块

### 2.1 获取当前用户信息

**GET** `/api/v1/users/me`

认证：是

#### 请求头

```
Authorization: Bearer <JWT Token>
```

#### 成功响应 (HTTP 200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "phone": "138****8000",
    "status": "NORMAL",
    "createdAt": "2026-05-17T10:00:00"
  }
}
```

#### 错误响应

| HTTP 状态码 | code | 场景 | message |
|------------|------|------|---------|
| 401 | 401 | 未携带 Token 或 Token 无效 | 请重新登录 |
| 404 | 404 | 用户不存在 | 用户不存在 |

#### 业务规则

1. 从 JWT Token 中提取 userId
2. 查询用户信息
3. 手机号脱敏：`138****8000`（第4-7位替换为 ****）
4. 只能访问自己的数据

---

### 2.2 修改密码

**PUT** `/api/v1/users/me/password`

认证：是

#### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| oldPassword | String | 是 | - | 旧密码 |
| newPassword | String | 是 | 8-20位，包含大写字母、小写字母、数字中的至少两种 | 新密码 |

#### 请求示例

```json
{
  "oldPassword": "Password1",
  "newPassword": "NewPass2"
}
```

#### 成功响应 (HTTP 200)

```json
{
  "code": 200,
  "message": "密码修改成功，请重新登录",
  "data": null
}
```

#### 错误响应

| HTTP 状态码 | code | 场景 | message |
|------------|------|------|---------|
| 400 | 400 | 旧密码不正确 | 旧密码不正确 |
| 400 | 400 | 新密码强度不足 | 密码须包含大写字母、小写字母、数字中的至少两种 |
| 400 | 400 | 新旧密码相同 | 新密码不能与旧密码相同 |
| 401 | 401 | 未携带 Token | 请重新登录 |

#### 业务规则

1. 验证旧密码正确性
2. 校验新密码强度
3. 校验新密码不等于旧密码
4. 更新密码哈希
5. 更新 passwordChangedAt 为当前时间（使所有已签发 Token 失效）
6. 记录审计日志：action=CHANGE_PASSWORD

---

## 3. 接口汇总

| 模块 | Method | URL | 描述 | 认证 | HTTP 成功状态码 |
|------|--------|-----|------|------|----------------|
| 认证 | POST | /api/v1/auth/register | 用户注册 | 否 | 201 |
| 认证 | POST | /api/v1/auth/login | 用户登录 | 否 | 200 |
| 用户 | GET | /api/v1/users/me | 获取当前用户信息 | 是 | 200 |
| 用户 | PUT | /api/v1/users/me/password | 修改密码 | 是 | 200 |

## 4. 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| mbank.jwt.secret | (Base64 编码 >= 256 位) | JWT 签名密钥 |
| mbank.jwt.expiration-hours | 24 | Token 有效期（小时） |
| mbank.security.max-login-attempts | 5 | 最大登录失败次数 |
| mbank.security.lock-duration-minutes | 15 | 临时锁定时长（分钟） |
| mbank.sms.mock-enabled | true | 是否启用 SMS Mock |
| mbank.sms.valid-code | 123456 | Mock 模式下有效验证码 |
