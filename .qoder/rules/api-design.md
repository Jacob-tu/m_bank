---
trigger: always_on
---
# API 设计规范 - RESTful

## URL 设计

### 基本规则

- 基础路径：`/api/v1`
- 资源名使用复数名词：`/api/v1/users`, `/api/v1/cards`
- 路径参数用于标识资源：`/api/v1/users/{id}`
- 查询参数用于过滤和分页：`/api/v1/transactions?page=0&size=20`

### M-Bank API 路由设计

| 模块 | Method | URL | 描述 | 认证 |
|------|--------|-----|------|------|
| 认证 | POST | /api/v1/auth/register | 用户注册 | 否 |
| 认证 | POST | /api/v1/auth/login | 用户登录 | 否 |
| 用户 | GET | /api/v1/users/me | 获取当前用户信息 | 是 |
| 用户 | PUT | /api/v1/users/me/password | 修改密码 | 是 |
| 银行卡 | GET | /api/v1/cards | 获取银行卡列表 | 是 |
| 银行卡 | POST | /api/v1/cards | 绑定银行卡 | 是 |
| 银行卡 | DELETE | /api/v1/cards/{id} | 解绑银行卡 | 是 |
| 银行卡 | PUT | /api/v1/cards/{id}/limit | 设置限额 | 是 |
| 交易 | POST | /api/v1/transactions/transfer | 发起转账 | 是 |
| 交易 | POST | /api/v1/transactions/payment | 发起缴费 | 是 |
| 交易 | GET | /api/v1/transactions | 查询交易流水 | 是 |
| 交易 | GET | /api/v1/transactions/{id} | 查询交易详情 | 是 |

## 请求格式

### 请求头

```
Content-Type: application/json
Authorization: Bearer <JWT Token>  （需要认证的接口）
```

### 请求体 (JSON)

```json
{
  "field1": "value1",
  "field2": "value2"
}
```

## 响应格式

### 统一响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

### 错误响应

```json
{
  "code": 400,
  "message": "手机号格式不正确",
  "data": null
}
```

### 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [ ],
    "totalElements": 100,
    "totalPages": 10,
    "number": 0,
    "size": 10
  }
}
```

## HTTP 状态码

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 | 成功 | GET、PUT 请求成功 |
| 201 | 已创建 | POST 创建资源成功 |
| 400 | 请求错误 | 参数校验失败 |
| 401 | 未认证 | Token 缺失或过期 |
| 403 | 禁止访问 | 无权限访问该资源 |
| 404 | 未找到 | 资源不存在 |
| 409 | 冲突 | 资源已存在（重复注册等） |
| 500 | 服务器错误 | 未预期的服务端异常 |

## 认证方案

- 认证方式：JWT Bearer Token
- Token 有效期：24 小时
- Token 结构：Header.Payload.Signature
- 需要认证的接口在文档中标注"认证：是"

## 版本管理

- URL 路径版本：`/api/v1/`
- 不兼容变更升级大版本
- 兼容性变更不升级版本
