# ================================================================
# M-Bank 后端 Dockerfile - 多阶段构建
# 阶段1: Maven 构建
# 阶段2: JRE 最小运行时
# ================================================================

# ---- 构建阶段 ----
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# 先复制依赖声明，利用 Docker 层缓存加速构建
COPY pom.xml .
# 下载依赖（离线缓存）
RUN apt-get update && apt-get install -y maven && \
    mvn dependency:go-offline -B

# 复制源码并编译（跳过测试，测试在 CI/CD 阶段执行）
COPY src ./src
RUN mvn package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-jammy AS runtime

# 创建非 root 用户，提升安全性
RUN groupadd --system mbank && useradd --system --gid mbank mbank

WORKDIR /app

# 从构建阶段复制 jar
COPY --from=builder /build/target/mbank-backend-*.jar app.jar

# 设置文件属主
RUN chown -R mbank:mbank /app

USER mbank

# 服务端口
EXPOSE 8080

# JVM 参数：
#   -XX:+UseContainerSupport       自动适配容器内存限制
#   -XX:MaxRAMPercentage=75.0      最多使用容器内存的 75%
#   -Djava.security.egd=...        加快随机数生成（SecureRandom）
#   -Dspring.profiles.active       通过环境变量覆盖 Profile
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
  "-jar", "app.jar"]

# 健康检查：每 30s 检查一次，启动等待 60s，超时 10s，失败 3 次标记不健康
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
