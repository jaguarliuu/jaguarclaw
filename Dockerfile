# ========================================
# JaguarClaw Backend Dockerfile
# Multi-stage build: Maven + JRE
# ========================================

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-24-alpine AS builder

WORKDIR /app

# 先复制 pom.xml，利用 Docker 缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:24-jre-alpine

LABEL maintainer="JaguarClaw"
LABEL description="JaguarClaw - Java AI Agent System"

WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1000 jaguarclaw && \
    adduser -u 1000 -G jaguarclaw -D jaguarclaw

# 从构建阶段复制 jar
COPY --from=builder /app/target/*.jar app.jar

# 创建工作目录
RUN mkdir -p /app/workspace && \
    chown -R jaguarclaw:jaguarclaw /app

USER jaguarclaw

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM 参数优化（容器环境）
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
