---
name: generate-test
description: 根据代码实现和需求文档自动生成测试用例，覆盖单元测试、集成测试和安全测试
---

# Skill: generate-test - 测试用例自动生成

## 触发方式

```
/generate-test <目标文件或模块路径>
```

## 执行步骤

### Step 1: 分析测试目标

读取目标代码文件，识别：
- 类的职责和公开方法
- 业务逻辑分支
- 异常处理路径
- 依赖的 Repository 和外部服务

同时读取对应的需求文档获取验收标准。

### Step 2: 生成单元测试

为 Service 层生成单元测试：
- 使用 JUnit 5 + Mockito
- Mock 依赖的 Repository 和外部服务
- 覆盖正向流程（Happy Path）
- 覆盖异常分支
- 边界值测试

输出到 `mbank-backend/src/test/java/com/mbank/service/`

### Step 3: 生成集成测试

为 Controller 层生成集成测试：
- 使用 MockMvc
- 测试 HTTP 状态码
- 测试响应体格式
- 测试认证鉴权

输出到 `mbank-backend/src/test/java/com/mbank/controller/`

### Step 4: 生成安全测试

为认证鉴权生成安全测试：
- 未认证访问拒绝
- Token 过期处理
- 越权访问拦截
- CORS 策略验证

输出到 `mbank-backend/src/test/java/com/mbank/security/`

### Step 5: 生成前端测试

为 Vue 组件生成测试：
- 组件渲染测试
- 用户交互测试
- 状态管理测试
- 路由导航测试

输出到 `mbank-frontend/src/__tests__/`

### Step 6: 覆盖率分析

分析生成的测试覆盖率：
- 识别未覆盖的代码路径
- 补充缺失的测试用例
- 确保核心业务逻辑覆盖率 >= 80%

## 测试命名规范

### 后端

```
方法名_场景_预期结果
例: login_withValidCredentials_returnsToken
例: transfer_withInsufficientBalance_throwsException
```

### 前端

```
描述 - 场景 - 预期
例: 'LoginForm - 提交有效凭证 - 跳转首页'
```

## 输出

```
mbank-backend/src/test/java/com/mbank/
├── service/         # Service 单元测试
├── controller/      # Controller 集成测试
└── security/        # 安全测试

mbank-frontend/src/__tests__/
├── components/      # 组件测试
├── stores/          # Store 测试
└── views/           # 页面测试
```
