# 开发说明

## 开发原则

- 后端业务逻辑优先放在 `service`，控制器只做请求接收和响应包装。
- 前端 API 统一放在 `frontend/api/index.js`。
- 教师端和学生端共用移动端项目，通过登录角色区分路由和权限。
- 管理后台独立在 `admin-frontend/`，面向管理员和教师数据看板。
- 生产密钥只放环境变量或服务器 `.env`，不提交到 Git。

## 后端开发

### 目录

```text
backend/src/main/java/com/papercritical/
|-- common/       通用响应、JWT 工具
|-- config/       Web、鉴权、跨域等配置
|-- controller/   REST API
|-- dto/          请求/响应 DTO
|-- entity/       数据库实体
|-- mapper/       MyBatis-Plus Mapper
|-- service/      业务服务
```

### 构建

```bash
cd backend
mvn -DskipTests package
```

### 新增公开接口

如果接口允许未登录访问，需要在 `WebConfig.java` 的拦截器白名单中加入路径。例如教师注册接口：

```java
if (path.endsWith("/api/auth/register")) {
    return true;
}
```

### 新增受保护接口

- 教师接口默认读取 `request.getAttribute("teacherId")`。
- 学生接口应放在 `/api/student/**` 下，并读取 `studentId`。
- 管理员接口放在 `/api/admin/**` 下。

## 移动端开发

### 目录

```text
frontend/
|-- api/index.js      API 封装、token、服务地址
|-- pages/login/      登录和注册
|-- pages/home/       教师首页
|-- pages/assignment/ 作业管理
|-- pages/student/    学生管理
|-- pages/grading/    批改流程和结果
|-- pages/student-portal/ 学生端作业和结果
```

### 角色处理

- 教师登录后保存 `teacherInfo` 和 `userRole=TEACHER`。
- 学生登录后保存 `studentInfo` 和 `userRole=STUDENT`。
- token 统一保存在本地存储中，由 API 封装自动放入 `Authorization`。

### 构建注意

当前命令行构建可能受 Node 24 影响。移动端生产打包建议使用 HBuilderX。

## 管理后台开发

### 目录

```text
admin-frontend/
|-- src/App.vue
|-- src/style.css
|-- vite.config.js
```

### 构建

```bash
cd admin-frontend
npm run build
```

部署到 `/admin/` 时，`vite.config.js` 需要：

```js
export default defineConfig({
  base: '/admin/'
})
```

## 验证清单

每次改动后建议至少执行：

```bash
cd backend
mvn -DskipTests package
```

如果改了管理后台：

```bash
cd admin-frontend
npm run build
```

如果改了移动端：

```bash
cd frontend
npm run build:mp-weixin
```

若移动端构建失败在 uni-cli/Node 兼容问题，使用 HBuilderX 做最终构建验证。

## 最近关键改动记录

- 增加教师注册接口 `/api/auth/register`。
- 登录页增加“登录/注册”切换。
- 修复答案页解析失败时的误导提示。
- MinerU 默认模型版本改为 `pipeline`。
- Web 管理后台已部署到 `/admin/`。
- 练习册答案页需要 `READY` 后才能批改学生提交。

## 安全注意

- 不在文档中写真实数据库密码、AI Key、微信 Secret、管理员密码。
- 账号清单如需保留，放在 `.docs/` 内部资料中，不面向公开发布。
- 线上部署前应确认 `.env` 权限为仅服务用户可读。
