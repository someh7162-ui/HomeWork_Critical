# 常见问题与排错指南

## 1. `Answer key is not ready yet`

含义：练习册作业的答案页还没有准备好，学生提交练习册时无法评分。

检查：

- 作业类型是否为 `WORKBOOK`。
- `answerKeyStatus` 是否为 `READY`。
- `answerKeyJson` 是否为空。

处理：

1. 教师重新上传答案页。
2. 等待答案页状态变为 `READY`。
3. 如果状态为 `FAILED`，查看失败提示。

## 2. 答案页提示图片解析失败

可能原因：

- 图片确实模糊、缺页、题号不完整。
- MinerU API Key 未配置。
- MinerU 模型版本配置异常。
- MinerU 上传或解析超时。

当前后端已经把失败提示改为更具体的原因，不应再统一误报“请上传更清晰的图片”。

服务器检查：

```bash
cd /home/wt/papercritical
tail -n 120 logs/app.log
grep '^MINERU_MODEL_VERSION=' .env
```

建议 `MINERU_MODEL_VERSION=pipeline`。

## 3. 移动端构建失败：`global.uniPlugin.options`

现象：

```text
TypeError: Cannot read properties of undefined (reading 'options')
```

原因：当前 uni-app CLI 与 Node 24 存在兼容问题。

处理：

- 使用 HBuilderX 打开 `frontend/` 运行到 Android 基座。
- 或切换到兼容的 Node LTS 版本后重新安装依赖。

## 4. 注册接口返回 401

说明 `/api/auth/register` 被鉴权拦截。

检查：

- `WebConfig.java` 中是否放行 `/api/auth/register`。
- 后端是否已经重新打包并部署。

验证：

```bash
curl -s -X POST http://127.0.0.1:8099/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{}'
```

正常应返回 `400` 业务校验，而不是 `401`。

## 5. 服务器端口 8099 不通

检查进程和端口：

```bash
pgrep -af 'paper-critical-backend-1.0.0.jar'
ss -ltnp | grep ':8099'
tail -n 120 /home/wt/papercritical/logs/app.log
```

常见原因：

- jar 没启动。
- 端口被旧进程占用。
- 数据库连接失败。
- 环境变量或 `.env` 配置错误。

## 6. 端口占用

日志中出现：

```text
Port 8099 is already in use
```

处理：

```bash
pgrep -af 'paper-critical-backend-1.0.0.jar'
kill -9 <PID>
cd /home/wt/papercritical
setsid -f sudo -u wt bash /home/wt/papercritical/start.sh >/dev/null 2>&1
```

确认只保留一个监听端口的 Java 进程。

## 7. 管理后台刷新 404

原因：Nginx 没有把 `/admin/` 子路径回退到 `index.html`。

配置：

```nginx
location /admin/ {
    try_files $uri $uri/ /admin/index.html;
}
```

## 8. 上传图片无法访问

检查：

- 后端 `upload.path` 是否正确。
- `WebConfig` 映射的 `/uploads/**` 是否指向实际上传目录。
- 图片文件是否存在。

服务器命令：

```bash
find /home/wt/papercritical/uploads -type f | tail
curl -I http://127.0.0.1:8099/uploads/20260607/example.jpg
```

## 9. 登录成功后看不到作业

教师端：

- 检查作业是否属于当前教师。
- 检查作业班级是否填写。

学生端：

- 检查学生 `teacherId` 是否正确。
- 检查学生 `className` 是否与作业 `className` 一致。

## 10. AI 批改失败

检查：

- AI Provider 配置是否正确。
- API Key 是否有效。
- 上传图片是否可读。
- 日志中是否有模型返回格式错误。

日志：

```bash
tail -n 200 /home/wt/papercritical/logs/app.log
```

批改失败后，系统会尝试返还已扣减额度。仍需关注日志中是否出现异常。
