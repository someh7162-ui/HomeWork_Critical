# 部署与运行指南

本文档说明本地运行、服务器部署和常见环境配置。不要把真实 API Key、数据库密码、JWT 密钥写入仓库。

## 环境要求

| 组件 | 建议版本 |
| --- | --- |
| Java | 11 |
| Maven | 3.8+ |
| Node.js | 管理后台可用新版 Node；移动端 uni-app 建议使用 HBuilderX 或兼容 Node 版本 |
| 数据库 | MySQL 或 PostgreSQL |
| Nginx | 用于 Web 管理后台和静态资源代理 |

## 后端本地运行

```bash
cd backend
mvn -DskipTests package
java -jar target/paper-critical-backend-1.0.0.jar
```

常用覆盖参数：

```bash
java -jar target/paper-critical-backend-1.0.0.jar \
  --server.port=8099 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/paper_critical \
  --spring.datasource.driver-class-name=org.postgresql.Driver \
  --spring.datasource.username=YOUR_DB_USER \
  --spring.datasource.password=YOUR_DB_PASSWORD \
  --upload.path=/path/to/uploads
```

## 后端环境变量

| 变量 | 说明 |
| --- | --- |
| `JWT_SECRET` | JWT 签名密钥 |
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `MINERU_API_KEY` | MinerU API Token |
| `MINERU_BASE_URL` | MinerU API 地址，默认 `https://mineru.net/api/v4` |
| `MINERU_MODEL_VERSION` | 建议 `pipeline` |
| `MIMO_API_KEY` | MiMo API Key |
| `QWEN_API_KEY` | Qwen API Key |
| `WECHAT_APPID` | 微信小程序 AppID |
| `WECHAT_APP_SECRET` | 微信小程序 Secret |
| `ADMIN_PASSWORD_HASH` | 管理员密码 BCrypt hash |

## 移动端运行

```bash
cd frontend
npm install
npm run build:mp-weixin
```

如果命令行构建遇到 `global.uniPlugin.options`，优先使用 HBuilderX 打开 `frontend/` 运行到 Android 基座或小程序。当前项目的移动端默认后端地址在 `frontend/api/index.js` 中配置。

## 管理后台本地运行

```bash
cd admin-frontend
npm install
npm run dev
```

构建：

```bash
npm run build
```

如果部署到 `/admin/` 子路径，确认 `admin-frontend/vite.config.js` 中 `base` 为 `/admin/`。

## 服务器部署流程

### 1. 打包后端

```bash
cd backend
mvn -DskipTests package
```

产物：

```text
backend/target/paper-critical-backend-1.0.0.jar
```

### 2. 上传并替换 jar

示例：

```bash
scp backend/target/paper-critical-backend-1.0.0.jar root@SERVER_IP:/tmp/paper-critical-backend-1.0.0.jar
ssh root@SERVER_IP
cd /home/wt/papercritical
cp paper-critical-backend-1.0.0.jar paper-critical-backend-1.0.0.jar.bak.$(date +%Y%m%d%H%M%S)
cp /tmp/paper-critical-backend-1.0.0.jar ./paper-critical-backend-1.0.0.jar
chown wt:wt paper-critical-backend-1.0.0.jar
```

### 3. 重启后端

```bash
cd /home/wt/papercritical
setsid -f sudo -u wt bash /home/wt/papercritical/start.sh >/dev/null 2>&1
```

检查：

```bash
pgrep -af 'paper-critical-backend-1.0.0.jar'
ss -ltnp | grep ':8099'
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8099/api/auth/register
```

### 4. 部署管理后台

```bash
cd admin-frontend
npm run build
```

将 `admin-frontend/dist/` 上传到服务器：

```bash
/usr/share/nginx/html/admin/
```

Nginx 示例：

```nginx
server {
    listen 80;
    server_name SERVER_IP;
    root /usr/share/nginx/html;
    index index.html;

    location /admin/ {
        try_files $uri $uri/ /admin/index.html;
    }
}
```

重载：

```bash
nginx -t
systemctl reload nginx
```

## 线上验证清单

- `GET /admin/` 能打开管理后台。
- `POST /api/auth/register` 未登录可访问，并返回业务校验或注册成功。
- `POST /api/auth/login` 教师可登录。
- `POST /api/auth/student-login` 学生可登录。
- `GET /uploads/...` 能访问已上传图片。
- 练习册答案页上传后状态能从 `PARSING` 变为 `READY` 或给出明确失败原因。
