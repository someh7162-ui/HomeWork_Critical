# API 接口说明

所有业务接口默认以 `/api` 开头。除登录、注册、微信登录、学生登录、管理员登录外，其余接口需要 `Authorization: Bearer <token>`。

## 认证接口

| 方法 | 路径 | 说明 | 登录要求 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | 教师账号登录 | 否 |
| `POST` | `/api/auth/register` | 教师账号注册，成功后返回 token | 否 |
| `POST` | `/api/auth/wechat-login` | 微信登录或创建教师账号 | 否 |
| `POST` | `/api/auth/student-login` | 学生账号登录 | 否 |
| `PUT` | `/api/auth/profile` | 更新教师资料 | 教师 |
| `POST` | `/api/auth/redeem` | 教师兑换卡密额度 | 教师 |

### 教师注册请求

```json
{
  "username": "teacher_demo",
  "password": "123456",
  "name": "张老师",
  "subject": "英语"
}
```

用户名要求 3-30 位，只能包含字母、数字和下划线；密码要求 6-32 位。

## 作业接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/assignments` | 创建作文或练习册作业 |
| `GET` | `/api/assignments` | 获取教师作业列表 |
| `GET` | `/api/assignments/{id}` | 获取作业详情 |
| `PUT` | `/api/assignments/{id}` | 更新作业 |
| `DELETE` | `/api/assignments/{id}` | 删除作业 |
| `POST` | `/api/assignments/{id}/answer-key` | 上传练习册答案页 |
| `GET` | `/api/assignments/{id}/answer-key` | 查看答案页解析状态 |
| `PUT` | `/api/assignments/{id}/answer-key` | 教师人工保存/修正结构化答案 |

作业类型：

- `ESSAY`: 作文作业。
- `WORKBOOK`: 练习册作业，需要答案页先解析完成。

## 学生管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/students` | 创建学生 |
| `GET` | `/api/students` | 获取学生列表 |
| `GET` | `/api/students/classes` | 获取班级列表 |
| `DELETE` | `/api/students/{id}` | 删除单个学生 |
| `DELETE` | `/api/students/class` | 删除整个班级 |
| `POST` | `/api/students/roster-ocr` | 花名册图片 OCR |
| `POST` | `/api/students/batch` | 批量创建学生 |

## 批改接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/essays/upload` | 上传作文图片并批改 |
| `POST` | `/api/essays/upload-workbook` | 上传练习册图片并逐题批改 |
| `GET` | `/api/essays/{id}` | 获取批改详情 |
| `GET` | `/api/essays/{id}/progress` | 获取批改进度 |
| `GET` | `/api/essays/assignment/{assignmentId}` | 获取某作业批改记录 |
| `GET` | `/api/essays/assignment/{assignmentId}/submissions` | 获取提交列表 |
| `GET` | `/api/essays/assignment/{assignmentId}/stats` | 获取作业统计 |
| `GET` | `/api/essays/assignment/{assignmentId}/export` | 导出作业成绩 |
| `POST` | `/api/essays/{id}/regrade` | 重新批改 |
| `PUT` | `/api/essays/{id}/final-score` | 保存最终分 |
| `PUT` | `/api/essays/{id}/review` | 教师复核 |

## 学生端接口

学生 token 只能访问 `/api/student/**`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/student/assignments` | 学生查看自己的作业 |
| `GET` | `/api/student/assignments/{assignmentId}` | 学生查看作业详情 |
| `POST` | `/api/student/assignments/{assignmentId}/submit` | 学生提交作文 |
| `POST` | `/api/student/assignments/{assignmentId}/workbook-submit` | 学生提交练习册 |
| `GET` | `/api/student/essays/{essayId}/progress` | 学生查看批改进度 |
| `GET` | `/api/student/essays/{essayId}` | 学生查看批改详情 |
| `GET` | `/api/student/summary` | 学生个人概览 |

## 管理后台接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/login` | 管理员登录 |
| `GET` | `/api/admin/dashboard` | 管理后台总览 |
| `GET` | `/api/admin/device` | 设备和服务连接状态 |
| `GET` | `/api/admin/teachers` | 教师列表 |
| `POST` | `/api/admin/teachers/{teacherId}/quota` | 给教师增加额度 |
| `GET` | `/api/admin/essays` | 批改记录 |
| `POST` | `/api/admin/cards/generate` | 生成卡密 |
| `GET` | `/api/admin/cards` | 卡密列表 |

## 额度和支付接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/quota/me` | 查看当前教师额度 |
| `POST` | `/api/pay/orders` | 创建支付订单 |
| `POST` | `/api/pay/orders/{orderNo}/mock-pay` | 模拟支付成功并发放额度 |

## 反馈接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/feedback` | 教师反馈列表 |
| `GET` | `/api/feedback/summary` | 教师反馈汇总 |
| `POST` | `/api/feedback` | 提交教师反馈 |
| `GET` | `/api/student/feedback` | 学生反馈列表 |
| `GET` | `/api/student/feedback/summary` | 学生反馈汇总 |
| `POST` | `/api/student/feedback` | 提交学生反馈 |

## 静态资源

| 路径 | 说明 |
| --- | --- |
| `/uploads/**` | 上传图片、批注图等静态资源 |
| `/admin/` | Web 管理后台静态页面 |
