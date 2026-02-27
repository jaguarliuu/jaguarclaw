# JaguarClaw ARM Linux 内网部署指南

## 1. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 24+ (aarch64) | 后端运行时 |
| Node.js | ≥20.19 或 ≥22.12 (aarch64) | 仅构建前端用，部署机可不装 |
| PostgreSQL | 16+ | 需 pgvector 扩展 |
| Nginx | 任意稳定版 | 托管前端静态文件 + 反代 WebSocket |

> 纯内网环境下，所有安装包需在有网机器下载后离线拷贝。

## 2. 离线包准备（在有网机器上完成）

### 2.1 JDK 24 aarch64

```bash
# 从 Adoptium 下载 aarch64 版本
# https://adoptium.net/temurin/releases/?os=linux&arch=aarch64&package=jdk&version=24
wget https://api.adoptium.net/v3/binary/latest/24/ga/linux/aarch64/jdk/hotspot/normal/eclipse
```

### 2.2 PostgreSQL 16 + pgvector

```bash
# 方式一：Docker 镜像离线导出
docker pull --platform linux/arm64 pgvector/pgvector:pg16
docker save pgvector/pgvector:pg16 -o pgvector-pg16-arm64.tar

# 方式二：系统包管理器（离线 rpm/deb）
```

### 2.3 构建后端 JAR

```bash
cd jaguarclaw
mvn clean package -DskipTests
# 产物：target/jaguarclaw.jar
```

### 2.4 构建前端

生产构建时 WebSocket 地址自动使用 `window.location.host`，无需手动修改。

```bash
cd jaguarclaw-ui
npm install
npm run build
# 产物：dist/ 目录
```

### 2.5 打包传输清单

```
jaguarclaw-deploy/
├── jdk-24-aarch64.tar.gz
├── pgvector-pg16-arm64.tar          # 或 rpm/deb
├── jaguarclaw.jar
├── jaguarclaw-ui-dist/              # dist/ 目录内容
├── skills/                          # .jaguarclaw/skills 完整拷贝
│   ├── agent-browser/
│   │   └── SKILL.md
│   ├── frontend-design/
│   │   └── SKILL.md
│   ├── pptx/
│   │   ├── SKILL.md
│   │   └── html2pptx.md
│   └── test-skill/
│       └── SKILL.md
├── application-prod.yml
└── nginx.conf
```

## 3. 目标机器部署

### 3.1 安装 JDK

```bash
mkdir -p /opt/jdk24
tar -xzf jdk-24-aarch64.tar.gz -C /opt/jdk24 --strip-components=1

cat > /etc/profile.d/jdk.sh << 'EOF'
export JAVA_HOME=/opt/jdk24
export PATH=$JAVA_HOME/bin:$PATH
EOF

source /etc/profile.d/jdk.sh
java -version
```

### 3.2 启动 PostgreSQL

**Docker 方式：**

```bash
docker load -i pgvector-pg16-arm64.tar
```

`docker-compose.yml`：

```yaml
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: jaguarclaw-postgres
    environment:
      POSTGRES_USER: jaguarclaw
      POSTGRES_PASSWORD: jaguarclaw        # 生产环境请改强密码
      POSTGRES_DB: jaguarclaw
    ports:
      - "5432:5432"
    volumes:
      - /data/jaguarclaw/pgdata:/var/lib/postgresql/data
    restart: always
```

```bash
docker-compose up -d
```

**原生安装方式：**

```bash
# 安装 PostgreSQL 16（离线 rpm/deb）
# 安装 pgvector 扩展
sudo -u postgres psql -c "CREATE USER jaguarclaw WITH PASSWORD 'jaguarclaw';"
sudo -u postgres psql -c "CREATE DATABASE jaguarclaw OWNER jaguarclaw;"
sudo -u postgres psql -d jaguarclaw -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 3.3 部署目录结构

```bash
mkdir -p /opt/jaguarclaw/.jaguarclaw/skills
mkdir -p /opt/jaguarclaw/workspace
mkdir -p /opt/jaguarclaw/www

cp jaguarclaw.jar /opt/jaguarclaw/
cp application-prod.yml /opt/jaguarclaw/
cp -r skills/* /opt/jaguarclaw/.jaguarclaw/skills/
cp -r jaguarclaw-ui-dist/* /opt/jaguarclaw/www/
```

最终结构：

```
/opt/jaguarclaw/
├── jaguarclaw.jar
├── application-prod.yml
├── workspace/                      # agent 工具读写目录
├── www/                            # 前端静态文件
│   ├── index.html
│   └── assets/
└── .jaguarclaw/
    └── skills/                     # 技能目录
        ├── agent-browser/
        │   └── SKILL.md
        ├── frontend-design/
        │   └── SKILL.md
        ├── pptx/
        │   ├── SKILL.md
        │   └── html2pptx.md
        └── test-skill/
            └── SKILL.md
```

#### Skills 加载路径

`SkillRegistry` 按优先级扫描三个目录：

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 0（最高） | `{user.dir}/.jaguarclaw/skills` | 项目级 |
| 1 | `{user.home}/.jaguarclaw/skills` | 用户级 |
| 2 | `{user.dir}/skills` | 内置 |

`user.dir` 是 `java -jar` 执行时的工作目录。**必须在 `/opt/jaguarclaw` 下启动 JAR**。

### 3.4 生产配置文件

`application-prod.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/jaguarclaw
    username: jaguarclaw
    password: jaguarclaw                    # 改为实际密码
  jpa:
    show-sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true

server:
  port: 8080

# LLM —— 指向内网模型服务
llm:
  # 兼容 OpenAI API 格式，代码自动追加 /v1（若 endpoint 不以 /v* 结尾）
  # Ollama:   http://10.0.0.100:11434
  # vLLM:     http://10.0.0.100:8000/v1
  # LocalAI:  http://10.0.0.100:8080
  endpoint: http://10.0.0.100:11434
  api-key: not-needed                     # Ollama 无需 key，填占位值
  model: qwen2.5:32b
  temperature: 0.7
  max-tokens: 8192
  timeout: 120

agent:
  system-prompt: |
    你是一个有帮助的 AI 助手。请用中文回答用户的问题。
    回答要简洁、准确、有条理。
  max-history-messages: 20
  loop:
    max-steps: 50
    run-timeout-seconds: 300
    step-timeout-seconds: 120
  hitl:
    timeout-seconds: 300

tools:
  workspace: /opt/jaguarclaw/workspace
  max-file-size: 1048576

skills:
  watch-enabled: false                    # 生产环境关闭热更新
  index-token-budget: 2000
```

### 3.5 启动后端

```bash
cd /opt/jaguarclaw
java -jar jaguarclaw.jar --spring.config.additional-location=file:./application-prod.yml
```

### 3.6 Systemd 服务

```bash
cat > /etc/systemd/system/jaguarclaw.service << 'EOF'
[Unit]
Description=JaguarClaw AI Agent
After=network.target postgresql.service

[Service]
Type=simple
User=jaguarclaw
WorkingDirectory=/opt/jaguarclaw
ExecStart=/opt/jdk24/bin/java -jar jaguarclaw.jar --spring.config.additional-location=file:./application-prod.yml
Restart=on-failure
RestartSec=10
Environment=JAVA_HOME=/opt/jdk24

[Install]
WantedBy=multi-user.target
EOF

useradd -r -s /sbin/nologin jaguarclaw
chown -R jaguarclaw:jaguarclaw /opt/jaguarclaw

systemctl daemon-reload
systemctl enable jaguarclaw
systemctl start jaguarclaw
```

### 3.7 Nginx 反代

`/etc/nginx/conf.d/jaguarclaw.conf`：

```nginx
server {
    listen 80;
    server_name _;

    # 前端静态文件
    root /opt/jaguarclaw/www;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # WebSocket 反代
    location /ws {
        proxy_pass http://127.0.0.1:8080/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

```bash
nginx -t && systemctl reload nginx
```

## 4. 验证

```bash
# 数据库连通
psql -h localhost -U jaguarclaw -d jaguarclaw -c "SELECT 1;"

# 后端启动
journalctl -u jaguarclaw -f
# 日志中应有：Loaded N skills from .../.jaguarclaw/skills

# 前端访问
# 浏览器打开 http://<机器IP>

# Skills 管理
# 进入 Settings → Skills，应显示已加载的技能列表
```

## 5. 常见问题

**Q: Skills 页面显示空白**
确认 `java -jar` 的工作目录是 `/opt/jaguarclaw`。`SkillRegistry` 通过 `System.getProperty("user.dir")` 获取路径。Systemd 中用 `WorkingDirectory` 指定。

**Q: WebSocket 连接失败**
检查 Nginx `/ws` 反代配置，确保 `proxy_read_timeout` 足够长（agent 运行可能耗时数分钟）。

**Q: 新增 Skill**
将 `SKILL.md` 及资源文件放入 `/opt/jaguarclaw/.jaguarclaw/skills/<name>/` 目录，重启服务。

**Q: 修改 LLM 模型**
编辑 `application-prod.yml` 中的 `llm.endpoint` / `llm.model`，重启服务。
