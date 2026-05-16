---
name: generate-frontend
description: 根据设计文档自动生成Vue3前端页面代码，包括路由、状态管理、API调用和页面组件
---

# Skill: generate-frontend - 前端页面生成

## 触发方式

```
/generate-frontend <设计文档路径或页面名>
```

## 执行步骤

### Step 1: 读取设计文档

读取以下设计文档：
- `mbank-docs/design/architecture.md` - 架构设计
- `mbank-docs/api/api-spec.md` - API 接口规范
- `mbank-docs/design/interaction-flow.md` - 交互流程

### Step 2: 生成 API 服务层

为每个后端 API 生成前端调用方法：
- Axios 请求封装
- 请求/响应类型定义
- 拦截器（Token 注入、错误处理）

输出到 `mbank-frontend/src/api/`

### Step 3: 生成状态管理

为每个业务模块生成 Pinia Store：
- 状态定义
- Actions（异步调用 API）
- Getters

输出到 `mbank-frontend/src/stores/`

### Step 4: 生成路由配置

生成 Vue Router 配置：
- 路由定义（含懒加载）
- 路由守卫（认证检查）
- 嵌套路由

输出到 `mbank-frontend/src/router/`

### Step 5: 生成页面组件

按模块生成 Vant4 移动端页面：

| 页面 | 组件 | 功能 |
|------|------|------|
| 登录 | LoginView.vue | 手机号+密码登录 |
| 注册 | RegisterView.vue | 手机号+密码+确认密码注册 |
| 首页 | HomeView.vue | 账户概览、快捷操作 |
| 银行卡列表 | CardListView.vue | 已绑银行卡列表 |
| 绑卡 | BindCardView.vue | 输入卡号绑定银行卡 |
| 转账 | TransferView.vue | 选择银行卡、输入金额转账 |
| 缴费 | PaymentView.vue | 水电缴费、话费充值 |
| 交易流水 | TransactionListView.vue | 交易记录分页列表 |
| 我的 | ProfileView.vue | 个人信息、修改密码 |

输出到 `mbank-frontend/src/views/`

### Step 6: 生成公共组件

- NavBar 顶栏
- AmountInput 金额输入
- CardItem 银行卡项
- TransactionItem 交易项

输出到 `mbank-frontend/src/components/`

### Step 7: 生成工具类

- Token 管理（localStorage）
- 请求封装（Axios 实例）
- 格式化工具（金额、日期、手机号脱敏）

输出到 `mbank-frontend/src/utils/`

## 代码规范

- 遵循 `.qoder/rules/coding-standard.md` 编码规范
- 遵循 `.qoder/rules/security-policy.md` 安全策略
- 移动端优先设计（Vant4 组件库）

## 输出

```
mbank-frontend/src/
├── api/           # API 调用层
├── stores/        # Pinia 状态管理
├── router/        # 路由配置
├── views/         # 页面组件
├── components/    # 公共组件
└── utils/         # 工具类
```
