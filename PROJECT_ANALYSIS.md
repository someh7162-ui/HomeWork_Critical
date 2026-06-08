# 智阅教育 项目结构与整体分析

## 1. 项目定位

智阅教育 是一个面向中小学英语教师的 AI 作文批改系统。教师可以通过移动端拍照上传学生作文，系统完成 OCR 识别、AI 评分、维度反馈、批改记录保存，并支持教师手动确认最终得分。

项目包含后端服务、uni-app 前端、数据库初始化脚本和微信小程序配置。

## 2. 技术栈

### 后端

- 框架：Spring Boot 2.7.18
- 语言版本：Java 11
- ORM：MyBatis-Plus 3.5.3.1
- 数据库：MySQL
- 认证：JWT
- 工具库：Hutool、Lombok
- 外部服务：SimpleTex OCR、小米 MiMo AI、微信小程序 code2session

### 前端

- 框架：uni-app
- 前端框架：Vue 3
- 构建工具：Vite
- 目标平台：微信小程序、App

### 数据库

- 数据库名：paper_critical
- 初始化脚本：`backend/src/main/resources/db/init.sql`
- 迁移脚本：`backend/src/main/resources/db/alter_*.sql`

## 3. 根目录结构

```text
智阅教育/
├── backend/                         # Spring Boot 后端项目
├── frontend/                        # uni-app 前端项目
├── PROJECT_STRUCTURE.md             # 原项目结构说明
├── PROJECT_ANALYSIS.md              # 当前项目结构与整体分析文档
├── project.config.json              # 微信小程序项目配置
└── project.private.config.json      # 微信开发者工具本地私有配置
```

## 4. 后端结构

```text
backend/
├── pom.xml                          # Maven 依赖与构建配置
├── src/
│   ├── main/
│   │   ├── java/com/papercritical/
│   │   │   ├── 智阅教育Application.java
│   │   │   ├── common/              # 通用返回对象、JWT 工具
│   │   │   ├── config/              # Web、CORS、静态资源、拦截器配置
│   │   │   ├── controller/          # REST API 控制器
│   │   │   ├── dto/                 # 请求/响应 DTO
│   │   │   ├── entity/              # 数据库实体类
│   │   │   ├── exception/           # 全局异常处理
│   │   │   ├── mapper/              # MyBatis-Plus Mapper
│   │   │   └── service/             # 业务服务层
│   │   └── resources/
│   │       ├── application.yml      # 后端配置文件
│   │       └── db/                  # 数据库初始化与变更脚本
│   └── test/                        # 测试代码目录
├── target/                          # Maven 构建产物
└── uploads/                         # 上传文件目录
```

### 4.1 Controller 层

```text
controller/
├── AuthController.java              # 登录、微信登录
├── AssignmentController.java        # 作业管理
├── StudentController.java           # 学生与班级管理、花名册 OCR
├── EssayController.java             # 作文上传、批改记录、重批、最终分
├── OcrController.java               # 单独 OCR 识别
├── QuotaController.java             # 批改额度查询
└── PaymentController.java           # 额度购买订单、模拟支付
```

### 4.2 Service 层

```text
service/
├── AuthService.java                 # 账号密码登录与 JWT 生成
├── WechatService.java               # 微信 code2session 登录
├── AssignmentService.java           # 作业创建、查询、删除
├── StudentService.java              # 学生管理、班级管理、花名册导入
├── EssayService.java                # 作文上传、OCR、AI 批改、结果保存
├── OcrService.java                  # SimpleTex OCR 调用
├── AiGradingService.java            # MiMo AI 评分与花名册解析
├── TeacherQuotaService.java         # 免费/付费额度扣减、退还、发放
└── PaymentOrderService.java         # 订单创建、模拟支付、额度发放
```

### 4.3 Entity 层

```text
entity/
├── Teacher.java                     # 教师
├── Assignment.java                  # 作业
├── Student.java                     # 学生
├── Essay.java                       # 作文批改记录
├── TeacherQuota.java                # 教师额度账户
├── QuotaUsageLog.java               # 额度使用日志
├── PaymentOrder.java                # 支付订单
└── PaymentCallbackLog.java          # 支付回调日志
```

### 4.4 DTO 层

```text
dto/
├── LoginDTO.java                    # 用户名密码登录请求
├── WechatLoginDTO.java              # 微信登录请求
├── AssignmentDTO.java               # 作业创建请求
├── EssayUploadDTO.java              # 作文上传请求
├── GradingResultDTO.java            # AI 批改结果
├── FinalScoreDTO.java               # 教师最终分请求
├── QuotaSummaryDTO.java             # 额度摘要
├── CreatePaymentOrderDTO.java       # 创建支付订单请求
└── PaymentOrderDTO.java             # 支付订单响应
```

## 5. 前端结构

```text
frontend/
├── App.vue                          # 应用入口与全局样式
├── main.js                          # uni-app 启动入口
├── manifest.json                    # 应用与小程序配置
├── pages.json                       # 页面路由与 tabBar 配置
├── package.json                     # 前端依赖与脚本
├── api/
│   └── index.js                     # API 请求封装
├── pages/
│   ├── login/                       # 登录页
│   ├── home/                        # 首页/作业概览
│   ├── assignment/                  # 作业列表、创建、详情
│   ├── student/                     # 学生、班级、花名册导入
│   └── grading/                     # 单篇批改、批量批改、结果、记录
├── node_modules/                    # 本地依赖目录
└── unpackage/                       # uni-app 构建产物
```

### 5.1 页面路由

```text
pages/
├── login/login.vue                  # 登录页，支持账号密码和微信登录
├── home/home.vue                    # 首页，展示作业、额度和快捷入口
├── assignment/list.vue              # 作业列表页
├── assignment/create.vue            # 创建作业页
├── assignment/detail.vue            # 作业详情页
├── student/list.vue                 # 学生/班级管理页
├── student/add.vue                  # 单个添加学生页
├── student/roster.vue               # 花名册导入页
├── student/class-create.vue         # 新建/编辑班级页
├── grading/capture.vue              # 单篇作文拍照批改页
├── grading/batch.vue                # 批量批改页
├── grading/result.vue               # 批改结果页
└── grading/list.vue                 # 批改记录列表页
```

### 5.2 TabBar

当前 `pages.json` 中配置了两个底部 Tab：

- 作业：首页 `pages/home/home`
- 学生：学生管理页 `pages/student/list`

## 6. 数据库结构

### 6.1 表清单

| 表名 | 说明 | 核心字段 |
| --- | --- | --- |
| `tb_teacher` | 教师表 | id, username, password, openid, name, subject |
| `tb_assignment` | 作业表 | id, teacher_id, title, question, model_essay, total_score |
| `tb_student` | 学生表 | id, teacher_id, name, class_name, student_no |
| `tb_essay` | 作文批改记录表 | id, assignment_id, student_id, image_url, ocr_text, ai_score, final_score, ai_feedback, status |
| `tb_teacher_quota` | 教师额度表 | teacher_id, free_quota_total, free_quota_used, paid_quota_total, paid_quota_used |
| `tb_payment_order` | 支付订单表 | order_no, teacher_id, amount, quota_amount, status |
| `tb_payment_callback_log` | 支付回调日志表 | order_no, payload, process_status |
| `tb_quota_usage_log` | 额度使用日志表 | teacher_id, usage_type, quota_source, change_amount |

### 6.2 主要关系

```text
tb_teacher 1 ── N tb_assignment
tb_teacher 1 ── N tb_student
tb_assignment 1 ── N tb_essay
tb_student 1 ── N tb_essay
tb_teacher 1 ── 1 tb_teacher_quota
tb_teacher 1 ── N tb_payment_order
tb_teacher 1 ── N tb_quota_usage_log
```

## 7. 核心接口

### 7.1 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 用户名密码登录 |
| POST | `/api/auth/wechat-login` | 微信小程序登录 |

### 7.2 作业

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/assignments` | 创建作业 |
| GET | `/api/assignments` | 获取作业列表 |
| GET | `/api/assignments/{id}` | 获取作业详情 |
| DELETE | `/api/assignments/{id}` | 删除作业 |

### 7.3 学生

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/students` | 添加学生 |
| GET | `/api/students` | 获取学生列表 |
| GET | `/api/students/classes` | 获取班级列表 |
| DELETE | `/api/students/{id}` | 删除学生 |
| DELETE | `/api/students/class` | 删除班级学生 |
| POST | `/api/students/roster-ocr` | 花名册 OCR 识别 |
| POST | `/api/students/batch` | 批量添加学生 |

### 7.4 作文批改

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/essays/upload` | 上传作文并批改 |
| GET | `/api/essays/{id}` | 获取批改详情 |
| GET | `/api/essays/assignment/{assignmentId}` | 获取某作业批改记录 |
| POST | `/api/essays/{id}/regrade` | 重新评分 |
| PUT | `/api/essays/{id}/final-score` | 保存教师最终分 |

### 7.5 OCR、额度与支付

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/ocr/recognize` | 单独 OCR 识别 |
| GET | `/api/quota/me` | 查询当前教师额度 |
| POST | `/api/pay/orders` | 创建额度购买订单 |
| POST | `/api/pay/orders/{orderNo}/mock-pay` | 模拟支付完成订单 |

## 8. 主要业务链路

### 8.1 登录链路

1. 前端提交账号密码或微信 code。
2. 后端校验用户或调用微信 code2session。
3. 后端生成 JWT。
4. 前端保存 token 和教师信息。
5. 后续请求通过 `Authorization: Bearer <token>` 鉴权。

### 8.2 作文批改链路

1. 教师选择作业和学生。
2. 前端拍照或选择作文图片。
3. 后端读取图片并计算 SHA256 哈希。
4. 如果同一作业、同一学生、同一图片已经批改过，则复用历史结果。
5. 如果没有历史记录，则预扣 1 次额度。
6. 后端保存图片到 `uploads/essays/yyyyMMdd/`。
7. 调用 OCR 服务识别作文文本。
8. 调用 AI 服务生成评分和反馈。
9. 保存批改记录到 `tb_essay`。
10. 前端展示批改结果，教师可保存最终分。

### 8.3 额度链路

1. 新教师默认拥有 50 篇免费额度。
2. 每次首次成功批改消耗 1 篇额度。
3. 相同作文图片重复上传会复用结果，不重复扣额度。
4. OCR 或 AI 失败时返还预扣额度。
5. 模拟支付成功后增加 500 篇付费额度。

## 9. 当前重点风险

### 9.1 安全风险

- `application.yml` 中存在数据库密码、JWT secret、OCR app-secret、AI api-key 等敏感信息。
- 教师密码当前为明文存储和明文比较。
- 部分详情接口缺少教师归属校验，可能存在越权访问。
- `/uploads/**` 公开暴露作文图片，涉及学生隐私。
- CORS 配置过宽，生产环境不安全。

### 9.2 业务风险

- 真实微信支付尚未完成，当前主要是模拟支付。
- 重新评分当前不消耗额度，可能与真实成本不一致。
- 作文批改链路较长，同步执行 OCR 和 AI，失败补偿依赖手动退款逻辑。
- 删除作业或学生时缺少关联数据处理，可能产生孤儿数据。

### 9.3 数据库风险

- `Assignment` 实体使用 `className`，但 `init.sql` 中 `tb_assignment` 未包含 `class_name` 字段。
- 初始化脚本和 alter 脚本分散，没有统一迁移管理工具。
- 数据库表缺少外键约束，数据一致性主要依赖业务代码。

### 9.4 前端风险

- 默认后端地址是内网 HTTP，不适合正式小程序环境。
- token 内存状态和本地存储可能不同步。
- 首页“已批改”统计实际等于作业数量，数据不准确。
- 批改记录页按 60/75/90 固定阈值判断等级，但系统多为 15 分制。
- 批量批改并发固定为 5，缺少暂停、重试和状态恢复机制。
- `node_modules/` 和 `unpackage/` 是依赖和构建产物，不建议纳入版本控制。

## 10. 建议改进顺序

1. 移除配置文件中的敏感信息，改用环境变量，并轮换已泄露密钥。
2. 修复所有基于 ID 的接口归属校验，避免越权访问。
3. 给 `tb_assignment` 补充 `class_name` 字段，并整理数据库迁移脚本。
4. 将教师密码改为 BCrypt 哈希存储。
5. 前端统一 request/upload 错误处理，修复 token 清理逻辑。
6. 修复首页统计和批改记录分数等级逻辑。
7. 关闭生产环境模拟支付，接入真实微信支付下单、验签、回调和幂等处理。
8. 将作文图片访问改为鉴权接口或私有对象存储签名 URL。
9. 将 OCR/AI 批改改造为异步任务，增加状态追踪、重试和失败补偿。
10. 增加 `.gitignore`，排除 `node_modules/`、`unpackage/`、`target/`、`uploads/` 等目录。

## 11. 总体评价

项目已经实现了 AI 作文批改系统的核心闭环，功能完整度较高，适合作为课程项目或原型系统演示。后端分层清楚，前端页面覆盖了教师端主要操作场景。

如果用于课程展示，建议优先修复数据库字段不一致、前端统计错误、分数等级错误和默认服务地址问题。

如果用于真实上线，必须优先解决密钥泄露、明文密码、越权访问、图片隐私、支付安全和数据库迁移治理等问题。
