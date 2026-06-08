# 智阅教育 项目结构文档

## 项目概述

智阅教育 是一个基于AI的英语作文批改系统，面向中小学英语教师。教师可以通过手机拍照上传学生作文，系统自动进行OCR识别和AI评分，并提供多维度的详细反馈。

## 技术栈

### 后端
- **框架**: Spring Boot 2.7.18
- **ORM**: MyBatis-Plus 3.5.3.1
- **数据库**: MySQL
- **认证**: JWT (jjwt 0.9.1)
- **工具库**: Hutool 5.8.23、Lombok
- **Java版本**: 11

### 前端
- **框架**: uni-app (Vue.js跨平台方案)
- **平台支持**: H5、微信小程序、Android、iOS

### AI服务
- **OCR识别**: SimpleTex API (APP鉴权 + MD5签名)
- **AI评分**: 小米MiMo v2 Omni视觉模型 (OpenAI兼容格式)

---

## 项目目录结构

```
智阅教育/
├── backend/                          # 后端项目
│   ├── pom.xml                       # Maven配置文件
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/papercritical/
│   │   │   │   ├── 智阅教育Application.java  # 启动类
│   │   │   │   ├── config/           # 配置类
│   │   │   │   ├── controller/       # 控制器层
│   │   │   │   │   ├── AuthController.java        # 认证接口
│   │   │   │   │   ├── AssignmentController.java  # 作业管理接口
│   │   │   │   │   ├── StudentController.java     # 学生管理接口
│   │   │   │   │   ├── EssayController.java       # 作文批改接口
│   │   │   │   │   └── OcrController.java         # OCR识别接口
│   │   │   │   ├── service/          # 服务层
│   │   │   │   │   ├── AuthService.java           # 认证服务
│   │   │   │   │   ├── AssignmentService.java     # 作业服务
│   │   │   │   │   ├── StudentService.java        # 学生服务
│   │   │   │   │   ├── EssayService.java          # 作文服务
│   │   │   │   │   ├── OcrService.java            # OCR服务
│   │   │   │   │   └── AiGradingService.java      # AI评分服务
│   │   │   │   ├── mapper/           # 数据访问层
│   │   │   │   ├── entity/           # 实体类
│   │   │   │   ├── dto/              # 数据传输对象
│   │   │   │   ├── common/           # 公共类
│   │   │   │   └── exception/        # 异常处理
│   │   │   └── resources/
│   │   │       ├── application.yml   # 应用配置
│   │   │       └── db/
│   │   │           └── init.sql      # 数据库初始化脚本
│   │   └── test/                     # 测试代码
│   └── uploads/                      # 文件上传目录
│
├── frontend/                         # 前端项目
│   ├── App.vue                       # 应用入口
│   ├── main.js                       # 主文件
│   ├── manifest.json                 # 应用配置
│   ├── pages.json                    # 页面路由配置
│   ├── api/
│   │   └── index.js                  # API接口定义
│   ├── pages/
│   │   ├── login/
│   │   │   └── login.vue             # 登录页
│   │   ├── home/
│   │   │   └── home.vue              # 首页(作业列表)
│   │   ├── assignment/
│   │   │   ├── list.vue              # 作业列表页
│   │   │   ├── create.vue            # 创建作业页
│   │   │   └── detail.vue            # 作业详情页
│   │   ├── student/
│   │   │   ├── list.vue              # 学生列表页
│   │   │   └── add.vue               # 添加学生页
│   │   └── grading/
│   │       ├── capture.vue           # 拍照批改页
│   │       ├── result.vue            # 批改结果页
│   │       └── list.vue              # 批改记录页
│   └── unpackage/                    # 打包输出目录
│
└── PROJECT_STRUCTURE.md              # 本文档
```

---

## 核心模块说明

### 1. 认证模块 (Auth)

**文件**: `AuthController.java` / `AuthService.java`

- 用户登录认证
- JWT Token生成与验证
- 密码校验（当前为明文，生产环境需加密）

**API接口**:
- `POST /api/auth/login` - 用户登录

### 2. 作业管理模块 (Assignment)

**文件**: `AssignmentController.java` / `AssignmentService.java`

- 创建、查询、删除作业
- 作业包含：标题、题目要求、参考范文、字数限制、满分、评分标准

**API接口**:
- `POST /api/assignments` - 创建作业
- `GET /api/assignments` - 获取作业列表
- `GET /api/assignments/{id}` - 获取作业详情
- `DELETE /api/assignments/{id}` - 删除作业

### 3. 学生管理模块 (Student)

**文件**: `StudentController.java` / `StudentService.java`

- 添加、查询、删除学生
- 学生包含：姓名、班级、学号

**API接口**:
- `POST /api/students` - 添加学生
- `GET /api/students` - 获取学生列表
- `DELETE /api/students/{id}` - 删除学生

### 4. 作文批改模块 (Essay)

**文件**: `EssayController.java` / `EssayService.java`

- 上传作文图片
- 调用OCR识别文字
- 调用AI进行评分
- 保存批改记录

**API接口**:
- `POST /api/essays/upload` - 上传并批改作文
- `GET /api/essays/{id}` - 获取批改详情
- `GET /api/essays/assignment/{assignmentId}` - 获取作业的批改记录
- `POST /api/essays/{id}/regrade` - 对已有批改记录重新评分
- `PUT /api/essays/{id}/final-score` - 老师手动保存最终得分

### 5. OCR识别模块

**文件**: `OcrController.java` / `OcrService.java`

- 调用SimpleTex API进行文字识别
- 支持原图识别和压缩图识别
- 多次重试机制（simpletex_ocr → doc_ocr）

**识别策略**:
1. 原图 + simpletex_ocr
2. 压缩图 + simpletex_ocr
3. 压缩图 + doc_ocr（兜底）

### 6. AI评分模块

**文件**: `AiGradingService.java`

- **视觉模式**：直接分析作文图片进行评分（推荐）
- **文本模式**：基于OCR识别文本进行评分（降级方案）

**评分维度**:
- 内容要点
- 语法与词汇
- 结构连贯性
- 卷面书写（仅视觉模式）

**输出格式**:
```json
{
  "totalScore": 85,
  "overallComment": "总体评价...",
  "dimensions": [
    {
      "name": "内容要点",
      "score": 22,
      "maxScore": 25,
      "comment": "该维度点评..."
    }
  ]
}
```

---

## 数据库设计

### 表结构

| 表名 | 说明 | 主要字段 |
|------|------|----------|
| tb_teacher | 教师表 | id, username, password, name, subject |
| tb_assignment | 作业表 | id, teacher_id, title, question, model_essay, total_score, scoring_criteria |
| tb_student | 学生表 | id, teacher_id, name, class_name, student_no |
| tb_essay | 作文批改记录表 | id, assignment_id, student_id, content_hash, image_url, ocr_text, ai_score, final_score, ai_feedback, status |

### 关系图

```
tb_teacher (1) ──< (N) tb_assignment
tb_teacher (1) ──< (N) tb_student
tb_assignment (1) ──< (N) tb_essay
tb_student (1) ──< (N) tb_essay
```

---

## 配置说明

### 后端配置 (application.yml)

```yaml
# 服务端口
server:
  port: 8099

# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/paper_critical
    username: root
    password: 123456

# JWT配置
jwt:
  secret: paper-critical-jwt-secret-key-2024-english-essay-grading
  expiration: 86400000  # 24小时

# OCR配置
ocr:
  app-id: xxx
  app-secret: xxx
  api-url: https://server.simpletex.cn/api/simpletex_ocr

# AI配置
mimo:
  api-key: xxx
  api-url: https://token-plan-cn.xiaomimimo.com/v1/chat/completions
  model: mimo-v2-omni
```

### 前端配置 (api/index.js)

```javascript
const BASE_URL = 'http://10.247.223.62:8099/api'  // 后端API地址
```

---

## 部署说明

### 后端部署

1. 创建MySQL数据库：`CREATE DATABASE paper_critical;`
2. 执行初始化脚本：`src/main/resources/db/init.sql`
3. 修改数据库连接配置
4. 配置OCR和AI服务的API密钥
5. 打包运行：`mvn clean package && java -jar target/paper-critical-backend-1.0.0.jar`

### 前端部署

1. 安装依赖：`npm install`
2. 开发调试：`npm run dev`
3. 打包构建：`npm run build`

---

## 项目特点

1. **AI驱动**: 结合OCR和大语言模型，实现智能作文批改
2. **多维度评分**: 从内容、语法、结构、卷面等多个维度进行评分
3. **视觉评分**: 支持直接分析作文图片，评估卷面书写质量
4. **跨平台**: 基于uni-app，支持H5、小程序、App多端运行
5. **完整功能**: 包含作业管理、学生管理、批改记录等完整业务流程
6. **评分稳定**: 同一学生在同一作业下上传完全相同的作文图片时，直接复用首次评分结果，避免分数波动
7. **教师定分**: 支持老师在 AI 评分后重新评分，并手动录入最终得分作为成绩依据
