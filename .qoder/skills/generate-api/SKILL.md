---
name: generate-api
description: 根据设计文档自动生成Spring Boot后端代码，包括Entity、Repository、Service、Controller和DTO各层
---

# Skill: generate-api - API 代码生成

## 触发方式

```
/generate-api <设计文档路径或模块名>
```

## 执行步骤

### Step 1: 读取设计文档

读取以下设计文档：
- `mbank-docs/design/data-model.md` - 数据模型
- `mbank-docs/api/api-spec.md` - API 接口规范
- `mbank-docs/design/architecture.md` - 架构设计

### Step 2: 生成 Entity 层

根据数据模型生成 JPA Entity：
- 类名、字段、类型
- 主键策略（自增）
- 表名映射
- 字段验证注解（@NotNull, @Size 等）
- 关联关系（@OneToMany, @ManyToOne 等）

输出到 `mbank-backend/src/main/java/com/mbank/entity/`

### Step 3: 生成 Repository 层

为每个 Entity 生成 Repository 接口：
- 继承 JpaRepository
- 自定义查询方法
- 分页查询支持

输出到 `mbank-backend/src/main/java/com/mbank/repository/`

### Step 4: 生成 DTO 层

为每个 API 接口生成请求/响应 DTO：
- 请求 DTO：携带验证注解
- 响应 DTO：脱敏处理（手机号等）
- 对象转换方法

输出到 `mbank-backend/src/main/java/com/mbank/dto/`

### Step 5: 生成 Service 层

为每个业务模块生成 Service：
- 接口定义
- 实现类
- 业务逻辑（含事务管理）
- 异常处理
- 审计日志记录

输出到 `mbank-backend/src/main/java/com/mbank/service/`

### Step 6: 生成 Controller 层

为每个 API 接口生成 Controller：
- REST 端点
- 请求参数验证
- 统一响应格式
- 认证要求标注

输出到 `mbank-backend/src/main/java/com/mbank/controller/`

### Step 7: 生成安全配置

- JWT 工具类
- Spring Security 配置
- 认证过滤器
- CORS 配置

输出到 `mbank-backend/src/main/java/com/mbank/config/` 和 `security/`

## 代码规范

- 遵循 `.qoder/rules/coding-standard.md` 编码规范
- 遵循 `.qoder/rules/api-design.md` API 设计规范
- 遵循 `.qoder/rules/security-policy.md` 安全策略

## 输出

```
mbank-backend/src/main/java/com/mbank/
├── entity/          # JPA 实体
├── repository/      # 数据访问层
├── dto/             # 数据传输对象
├── service/         # 业务逻辑层
│   └── impl/        # 业务实现
├── controller/      # REST 控制器
├── config/          # 配置类
└── security/        # 安全组件
```
