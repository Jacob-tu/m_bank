# M-Bank AI 原生研发工艺 - Agent 全局配置

## 项目概述

M-Bank 是一个简易手机银行核心业务系统，采用前后端分离架构：
- 前端：Vue3 + Vant4（移动端 H5/Web）
- 后端：Java + Spring Boot 3.2+
- 数据库：MySQL 8.0+

## AI 原生研发理念

本项目采用 AI 原生研发模式，核心原则：
1. **AI 优先**：所有研发环节优先考虑 AI 辅助或自动化
2. **工艺沉淀**：每个研发活动都产出可复用的 Agent/Skill/Rule/Template
3. **闭环驱动**：需求 -> 设计 -> 编码 -> 审查 -> 测试 -> 部署，端到端 AI 闭环
4. **质量门禁**：每个阶段设置 AI 驱动的质量检查点
5. **持续演进**：工艺规范随项目推进不断迭代优化

## 注册 Agent

| Agent | 文件 | 职责 |
|-------|------|------|
| requirement-analyst | agents/requirement-analyst.md | 需求分析、用户故事拆分、验收标准生成 |
| architect | agents/architect.md | 架构设计、技术方案、数据模型 |
| code-reviewer | agents/code-reviewer.md | 代码审查、安全扫描、规范检查 |
| test-generator | agents/test-generator.md | 测试用例生成、覆盖率分析 |
| devops | agents/devops.md | 部署策略、环境配置、监控告警 |

## 注册 Skill

| Skill | 文件 | 触发方式 |
|-------|------|----------|
| requirement-to-design | skills/requirement-to-design.md | /requirement-to-design |
| generate-api | skills/generate-api.md | /generate-api |
| generate-frontend | skills/generate-frontend.md | /generate-frontend |
| generate-test | skills/generate-test.md | /generate-test |
| security-scan | skills/security-scan.md | /security-scan |
| deploy-assist | skills/deploy-assist.md | /deploy-assist |

## 研发流程编排

### 端到端研发流程

```
业务需求 -> [requirement-analyst] -> 需求文档
  -> [architect] -> 技术方案
  -> [generate-api / generate-frontend] -> 代码
  -> [code-reviewer] -> 审查报告
  -> [generate-test] -> 测试用例
  -> [devops] -> 部署配置
```

### 质量门禁

| 阶段 | 门禁 | 标准 |
|------|------|------|
| 需求 | 需求完整性检查 | 所有用户故事含验收标准 |
| 设计 | 方案评审 | 架构图 + 数据模型 + API 设计完整 |
| 编码 | 代码审查 | 无安全漏洞、符合编码规范 |
| 测试 | 覆盖率检查 | 核心业务逻辑 >= 80% |
| 部署 | 健康检查 | 所有服务健康、无告警 |

## 项目约束

- Java 版本：8+（推荐 17）
- MySQL 版本：8.0+
- 前端构建工具：Vite
- API 风格：RESTful
- 认证方式：JWT
- 容器化：Docker + Docker Compose
