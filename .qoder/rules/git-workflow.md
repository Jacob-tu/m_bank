---
trigger: always_on
---
# Git 工作流规范

## 分支策略

### 分支模型

```
main (生产分支)
  └── develop (开发分支)
       ├── feature/account-login      (功能分支)
       ├── feature/bank-card          (功能分支)
       ├── feature/transaction        (功能分支)
       └── feature/security           (功能分支)
```

### 分支命名

| 类型 | 格式 | 示例 |
|------|------|------|
| 功能 | feature/<模块>-<功能> | feature/account-login |
| 修复 | fix/<模块>-<问题> | fix/transfer-amount-check |
| 热修复 | hotfix/<模块>-<问题> | hotfix/auth-token-expire |

## 提交规范

### Commit Message 格式

```
<type>(<scope>): <subject>

<body>
```

### Type 定义

| Type | 含义 | 示例 |
|------|------|------|
| feat | 新功能 | feat(auth): 实现JWT认证 |
| fix | 修复缺陷 | fix(transfer): 修复余额校验 |
| docs | 文档更新 | docs(api): 更新API文档 |
| style | 代码格式 | style: 格式化代码 |
| refactor | 重构 | refactor(user): 拆分用户服务 |
| test | 测试 | test(transfer): 添加转账测试 |
| chore | 构建/工具 | chore: 更新依赖版本 |

### Scope 定义

| Scope | 模块 |
|-------|------|
| auth | 认证鉴权 |
| user | 用户管理 |
| card | 银行卡管理 |
| transaction | 交易 |
| security | 安全 |
| infra | 基础设施 |

### Commit 示例

```
feat(auth): 实现用户注册和登录

- 添加用户注册接口 POST /api/v1/auth/register
- 添加用户登录接口 POST /api/v1/auth/login
- 实现 JWT Token 生成和验证
- 密码使用 BCrypt 加密存储
```

## 合并规范

1. 功能分支通过 Pull Request 合并到 develop
2. PR 必须通过代码审查（code-reviewer Agent）
3. PR 必须通过所有测试
4. 合并使用 Squash Merge，保持提交历史整洁
5. 合并后删除功能分支

## .gitignore

### 后端忽略

```
target/
*.class
*.jar
*.war
.idea/
*.iml
```

### 前端忽略

```
node_modules/
dist/
.env.local
```

### 通用忽略

```
.DS_Store
Thumbs.db
*.log
```
