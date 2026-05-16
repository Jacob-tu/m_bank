---
name: deploy-assist
description: 根据项目代码自动生成容器化配置和部署方案，包括Dockerfile、Docker Compose、Nginx配置等
---

# Skill: deploy-assist - 部署辅助

## 触发方式

```
/deploy-assist
```

## 执行步骤

### Step 1: 分析项目结构

读取项目结构和配置：
- 后端：pom.xml / build.gradle 依赖
- 前端：package.json 依赖
- 数据库：初始化脚本

### Step 2: 生成后端 Dockerfile

基于 Spring Boot 项目生成多阶段构建 Dockerfile：
- 构建阶段：Maven/Gradle 编译
- 运行阶段：JRE 最小镜像
- 健康检查配置
- JVM 参数优化

输出到 `mbank-backend/Dockerfile`

### Step 3: 生成前端 Dockerfile

基于 Vue3 项目生成多阶段构建 Dockerfile：
- 构建阶段：npm install + vite build
- 运行阶段：Nginx 静态资源服务
- Gzip 压缩配置

输出到 `mbank-frontend/Dockerfile`

### Step 4: 生成 Docker Compose

生成开发环境编排文件：
- backend 服务（Spring Boot）
- frontend 服务（Nginx + Vue3）
- mysql 服务（MySQL 8.0）
- 网络和卷配置
- 环境变量配置

输出到 `docker-compose.yml`

### Step 5: 生成 Nginx 配置

生成反向代理配置：
- /api/* 代理到后端
- /* 静态资源服务
- Gzip 压缩
- 安全头配置

输出到 `nginx/default.conf`

### Step 6: 生成数据库初始化脚本

生成 MySQL 初始化 SQL：
- 创建数据库
- 建表语句
- 初始数据（如需要）

输出到 `mbank-backend/sql/init.sql`

### Step 7: 生成环境配置

生成多环境配置：
- application-dev.yml（开发环境）
- application-prod.yml（生产环境）
- .env 文件模板

输出到 `mbank-backend/src/main/resources/`

### Step 8: 生成健康检查

为后端添加健康检查端点：
- Spring Boot Actuator 配置
- 数据库连接检查
- 磁盘空间检查

## 输出

```
m_bank/
├── docker-compose.yml
├── mbank-backend/
│   ├── Dockerfile
│   └── sql/init.sql
├── mbank-frontend/
│   └── Dockerfile
└── nginx/
    └── default.conf
```
