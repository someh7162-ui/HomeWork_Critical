# Android 基座运行指南

本文档用于将 智阅教育 前端运行到 Android 基座，并通过局域网连接本机后端。

## 1. 运行模式

当前 Android App 采用局域网调试模式：

```text
Android 手机 App -> 同一 Wi-Fi -> 电脑 Spring Boot 后端 -> MySQL / OCR / AI 服务
```

手机不能填写 `localhost` 或 `127.0.0.1`，因为这两个地址在手机上指向手机自身。需要填写电脑的局域网 IP。

## 2. 启动后端

### 2.1 启动 MySQL

确保本机 MySQL 已启动，并已经执行初始化脚本：

```text
backend/src/main/resources/db/init.sql
```

默认数据库连接在：

```text
backend/src/main/resources/application.yml
```

默认后端端口：

```text
8099
```

### 2.2 启动 Spring Boot

可以用 IDE 启动：

```text
backend/src/main/java/com/papercritical/PaperCriticalApplication.java
```

也可以在 `backend` 目录运行：

```bash
mvn spring-boot:run
```

启动成功后，后端地址类似：

```text
http://电脑局域网IP:8099
```

## 3. 查看电脑局域网 IP

Windows 可以在命令行运行：

```bash
ipconfig
```

找到当前 Wi-Fi 网卡下的 IPv4 地址，例如：

```text
IPv4 地址 . . . . . . . . . . . . : 192.168.1.8
```

那么 App 登录页服务器地址填写：

```text
192.168.1.8:8099
```

也可以填写完整地址：

```text
http://192.168.1.8:8099
```

## 4. 运行到 Android 基座

### 4.1 使用 HBuilderX

1. 用 HBuilderX 打开 `frontend/` 目录。
2. 手机开启 USB 调试并连接电脑。
3. 在 HBuilderX 中选择：

```text
运行 -> 运行到手机或模拟器 -> 运行到 Android App 基座
```

4. App 安装后打开登录页。
5. 服务器地址填写电脑局域网地址，例如：

```text
192.168.1.8:8099
```

6. 使用测试账号登录：

```text
用户名：teacher1
密码：123456
```

登录页会记住上次填写的服务器地址。可以填写 `192.168.1.8:8099`、`http://192.168.1.8:8099` 或带 `/api` 的地址，App 会自动规范化为后端服务根地址。

### 4.2 使用命令行构建

前端目录提供了 App 脚本：

```bash
npm run dev:app-plus
```

实际运行到手机仍建议使用 HBuilderX，因为 Android 基座调试、设备选择和日志查看更方便。

当前项目依赖的 uni-app CLI 在 Node 24 环境下可能出现 `global.uniPlugin.options` 相关报错。课程演示优先使用 HBuilderX 运行到 Android 基座；如需命令行构建，建议后续单独统一升级 DCloud 依赖或切换到兼容的 Node LTS 环境后再验证。

## 5. 已做的 Android 适配

- 登录页 Android 端隐藏微信一键登录，使用账号密码登录。
- 登录页增加局域网地址提示。
- API 层每次请求读取最新 token，401 时清空登录态。
- 上传接口增加统一 JSON 解析和错误处理。
- Android manifest 增加明文 HTTP 访问配置，便于访问 `http://192.168.x.x:8099`。
- 首页隐藏购买额度入口，当前版本不做变现。
- 拍照上传去掉强依赖裁切，提升 Android 基座兼容性。
- 批量批改并发从 5 降为 3，降低手机和后端压力。

## 6. 常见问题

### 6.1 手机登录提示网络请求失败

检查：

1. 手机和电脑是否连接同一个 Wi-Fi。
2. 登录页是否填写电脑 IP，而不是 `localhost`。
3. 后端是否已经启动在 8099 端口。
4. Windows 防火墙是否拦截 8099 端口或 Java 程序。

可以在手机浏览器访问：

```text
http://电脑局域网IP:8099
```

如果浏览器也访问不了，说明不是 App 问题，而是网络、后端或防火墙问题。

### 6.2 账号密码错误

初始化脚本内置测试账号：

```text
teacher1 / 123456
```

如果数据库不是新初始化的，请检查 `tb_teacher` 表。

### 6.3 创建作业失败

如果报 `class_name` 字段不存在，说明数据库结构和实体不一致。需要给 `tb_assignment` 增加字段：

```sql
ALTER TABLE tb_assignment ADD COLUMN class_name VARCHAR(100) COMMENT '班级';
```

### 6.4 拍照或选图无反应

检查：

1. App 是否授予相机权限。
2. App 是否授予相册/文件访问权限。
3. 是否在 Android 基座中运行，而不是微信小程序模拟器。

### 6.5 上传后长时间等待

作文批改链路包含图片上传、OCR 和 AI 评分，耗时可能较长。建议：

1. 使用清晰图片。
2. 手机和电脑连接稳定 Wi-Fi。
3. 后端 OCR/AI key 可用。
4. 先用单篇批改验证，再使用批量批改。

## 7. 注意事项

- 当前版本是局域网调试 App，不适合作为公网正式发布版本。
- 如果需要长期使用，建议将后端部署到公网 HTTPS 服务。
- 生产环境应移除配置文件中的敏感密钥，改用环境变量。
- 生产环境应关闭模拟支付，并实现真实支付流程或完全移除支付模块。
