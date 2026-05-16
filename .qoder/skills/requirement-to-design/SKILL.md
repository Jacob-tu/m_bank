---
name: requirement-to-design
description: 将业务需求文档端到端转化为技术方案设计，包括架构设计、数据模型、API接口和交互流程
---

# Skill: requirement-to-design - 需求到设计转化

## 触发方式

```
/requirement-to-design <需求文档路径>
```

## 执行步骤

### Step 1: 读取需求文档

读取 `mbank-docs/requirements/` 下的需求文档，提取：
- 业务实体列表
- 用户故事列表
- 验收标准列表
- 非功能需求

### Step 2: 生成架构设计

基于需求生成系统架构方案：
- 分层架构图
- 模块划分
- 技术选型说明
- 部署架构

输出到 `mbank-docs/design/architecture.md`

### Step 3: 生成数据模型

基于业务实体设计数据模型：
- ER 关系图（Mermaid 格式）
- 表结构定义（表名、字段、类型、约束）
- 索引设计

输出到 `mbank-docs/design/data-model.md`

### Step 4: 生成 API 接口设计

基于用户故事设计 RESTful API：
- 接口列表（URL、Method、描述）
- 请求/响应格式
- 状态码定义
- 认证要求

输出到 `mbank-docs/api/api-spec.md`

### Step 5: 生成交互流程

基于核心业务流程设计交互时序：
- 登录流程时序图
- 转账流程时序图
- 绑卡流程时序图

输出到 `mbank-docs/design/interaction-flow.md`

### Step 6: 完整性自检

对照需求文档检查设计完整性：
- [ ] 每个用户故事都有对应的 API 接口
- [ ] 每个业务实体都有对应的数据表
- [ ] 认证鉴权方案已覆盖所有接口
- [ ] 审计日志方案已覆盖所有资金操作

## 输出

```
mbank-docs/
├── design/
│   ├── architecture.md      # 架构设计
│   ├── data-model.md        # 数据模型
│   └── interaction-flow.md  # 交互流程
└── api/
    └── api-spec.md          # API 接口规范
```
