# PaperCritical 项目说明

PaperCritical 是一个面向教师和学生的 AI 作业批改平台，支持作文批改、练习册逐题批改、答案页解析、学生自主提交、教师复核、班级统计、后台运营管理和移动端应用打包。

当前项目包含三个主要端：

- `backend/`: Spring Boot 后端服务，提供认证、作业、学生、批改、统计、额度、支付模拟、后台管理等 API。
- `frontend/`: uni-app 移动端，覆盖教师端和学生端，可运行到 Android 基座、小程序等平台。
- `admin-frontend/`: Vue + Vite 网页后台，用于管理员和教师的数据看板。

## 快速入口

| 文档 | 用途 |
| --- | --- |
| [PROJECT_ARCHITECTURE.md](PROJECT_ARCHITECTURE.md) | 项目架构、目录结构、核心流程 |
| [API_REFERENCE.md](API_REFERENCE.md) | 后端 API 分组说明 |
| [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) | 本地运行、服务器部署、Nginx 配置 |
| [USER_MANUAL.md](USER_MANUAL.md) | 教师、学生、管理员使用说明 |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | 常见报错与处理办法 |
| [DEVELOPMENT_NOTES.md](DEVELOPMENT_NOTES.md) | 开发约定、配置、构建验证 |

## 核心能力

- 教师账号登录、注册、资料维护。
- 学生账号由教师创建，学生登录后只看到所属班级作业。
- 支持作文作业和练习册作业两种类型。
- 练习册作业支持答案页上传、MinerU 结构化解析、AI 逐题评分。
- 学生端支持多页图片提交，并可查看批改进度和结果。
- 教师端支持代学生上传、查看提交列表、统计平均分、薄弱知识点和最终复核。
- 后端支持真实批注图生成，结果页优先展示后端合成的批注成品图。
- 管理后台支持设备连接状态、教师列表、批改记录、额度卡密等运营视图。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 11, Spring Boot 2.7.18, MyBatis-Plus, JWT, BCrypt |
| 数据库 | 本地可用 MySQL, 线上当前使用 PostgreSQL |
| 移动端 | uni-app, Vue 3 |
| 管理后台 | Vue 3, Vite |
| AI/OCR | MinerU, SimpleTex, MiMo/Qwen 兼容配置 |
| 部署 | Linux, Nginx, Java jar, 静态资源部署 |

## 目录概览

```text
PaperCritical/
|-- backend/                 Spring Boot 后端
|-- frontend/                uni-app 移动端
|-- admin-frontend/          Web 管理后台
|-- deploy/                  部署配置和验证脚本
|-- .docs/                   内部资料和账号清单
|-- README.md                项目总入口
```

## 当前线上形态

- 后端端口: `8099`
- Web 管理后台路径: `/admin/`
- 上传文件由后端映射为 `/uploads/**`
- 生产密钥通过服务器环境变量或 `.env` 注入，不应写入代码仓库或公开文档。

## 常用命令

```bash
# 后端构建
cd backend
mvn -DskipTests package

# 管理后台构建
cd admin-frontend
npm run build

# 移动端构建
cd frontend
npm run build:mp-weixin
```

注意：当前本机 Node 24 下 uni-app CLI 可能触发 `global.uniPlugin.options` 兼容问题，移动端建议使用 HBuilderX 或兼容 Node 版本构建。
