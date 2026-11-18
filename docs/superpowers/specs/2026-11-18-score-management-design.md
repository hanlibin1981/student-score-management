# 学生成绩管理系统 — 设计文档

- 日期：2026-11-18
- 状态：已确认
- 范围：登录认证、学生信息管理、课程/考试/成绩管理、成绩分析

## 1. 目标与范围

构建一套前后端分离的学生成绩管理系统，支持管理员、教师、学生三种角色：

- **管理员**：管理学生档案、课程、教师/学生账号、成绩，查看全校分析。
- **教师**：管理自己所授课程的考试与成绩，查看自己课程的分析。
- **学生**：查看自己的成绩、排名与成绩趋势。

不在本期范围：选课、请假、通知公告、数据导入导出、文件上传、多租户。

## 2. 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17（编译目标，运行环境 Java 20 可用）、Spring Boot 3.x、Spring Security + JWT（jjwt 0.12.x）、MyBatis-Plus 3.5.x（`mybatis-plus-spring-boot3-starter`）、jakarta validation |
| 数据库 | MySQL 8（开发/运行），测试用 H2（MODE=MySQL） |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc；Vitest |
| 构建 | Maven Wrapper（`mvnw`，无需本机安装 Maven） |

## 3. 系统架构

```
score/
├── backend/
│   └── src/main/java/com/score/
│       ├── config/          Security、CORS、MyBatis-Plus、初始化数据
│       ├── security/        JWT 工具、JWT 认证过滤器、UserDetailsService
│       ├── controller/      Auth / Student / Course / Exam / Score / Analysis / User
│       ├── service/         业务接口与实现（含数据归属校验）
│       ├── mapper/          MyBatis-Plus Mapper（复杂统计 SQL）
│       ├── entity/ dto/ vo/ 实体、入参 DTO、出参 VO（VO 不含 password）
│       └── common/          Result、BusinessException、GlobalExceptionHandler、常量
├── frontend/src/
│   ├── api/                 axios 实例 + 各资源接口封装
│   ├── router/              路由 + 角色守卫
│   ├── stores/              Pinia（auth）
│   ├── layouts/             主布局（侧边栏 + 顶栏）
│   ├── views/               页面
│   └── components/          BaseChart 等复用组件
├── docs/superpowers/specs/  本设计文档
└── README.md
```

- 后端端口 8080，接口统一前缀 `/api`。
- 前端端口 5173，Vite 代理 `/api` → `http://localhost:8080`。
- 可选单 jar 部署：`npm run build` 产物放入 `backend/src/main/resources/static`。

## 4. 数据模型

库名 `score_db`，字符集 `utf8mb4`。

### sys_user
| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| username | VARCHAR(50) | UNIQUE, NOT NULL |
| password | VARCHAR(100) | NOT NULL，BCrypt |
| real_name | VARCHAR(50) | |
| role | VARCHAR(20) | ADMIN / TEACHER / STUDENT |
| enabled | TINYINT | 默认 1 |
| created_at | DATETIME | |

### student
| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| student_no | VARCHAR(20) | UNIQUE, NOT NULL |
| name | VARCHAR(50) | NOT NULL |
| gender | VARCHAR(10) | |
| class_name | VARCHAR(50) | |
| phone | VARCHAR(20) | |
| email | VARCHAR(100) | |
| user_id | BIGINT | 可空，关联 sys_user |
| created_at | DATETIME | |

### course
| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| course_code | VARCHAR(20) | UNIQUE, NOT NULL |
| name | VARCHAR(100) | NOT NULL |
| credit | DECIMAL(3,1) | |
| semester | VARCHAR(20) | |
| teacher_id | BIGINT | 关联 sys_user，可空 |
| created_at | DATETIME | |

### exam
| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| course_id | BIGINT | NOT NULL，关联 course |
| name | VARCHAR(50) | NOT NULL，(course_id, name) 唯一 |
| exam_date | DATE | |
| full_score | INT | 默认 100，> 0 |

### score
| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| student_id | BIGINT | NOT NULL |
| exam_id | BIGINT | NOT NULL |
| score | DECIMAL(5,1) | 0 ≤ score ≤ exam.full_score |
| updated_at | DATETIME | |
| | | UNIQUE(student_id, exam_id) |

### 业务规则

1. **课程总评** = 该学生该课程各次考试 `score / full_score` 归一后的算术平均 × 100，保留 1 位小数。
2. **排名**：同一课程范围内按总评降序，同分同名次（1, 2, 2, 4）。
3. **成绩范围**：`0 ≤ score ≤ full_score`，后端强校验。
4. **重复录入**：同一 `(student_id, exam_id)` 再次保存即更新，不报错。
5. **及格**：默认 60 分（归一化后），预警阈值前端可调（接口参数 `threshold`）。
6. **学生与账号**：学生档案与登录账号通过 `student.user_id` 关联；开通账号时 `username = student_no`，初始密码 `123456`。

## 5. 认证与授权

- 登录 `POST /api/auth/login`：校验 BCrypt 密码 → 签发 JWT（HS256，24h），载荷 `{userId, username, role}`。
- Security 配置：`/api/auth/login` 放行，其余需认证；无状态会话。
- 401（未认证）与 403（无权限）分开返回。
- 角色控制：`@PreAuthorize` 声明接口级角色要求。
- 数据归属校验（Service 层）：
  - 教师只能操作 `teacher_id = 当前用户` 的课程下的考试与成绩；
  - 教师查看分析时自动限定到自己课程；
  - 学生只能查询 `student.user_id = 当前用户` 的成绩与排名。
- 账号禁用（enabled = 0）后登录被拒绝。

## 6. API 契约

统一响应：`{ code: 0, message: "ok", data: ... }`；分页数据 `{ list, total, page, size }`。

### 认证
| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录，返回 `{token, user}` | 公开 |
| GET | `/api/auth/me` | 当前用户信息 | 已登录 |
| PUT | `/api/auth/password` | 修改自己的密码 | 已登录 |

### 学生
| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/students?keyword=&className=&page=&size=` | 分页查询 | ADMIN、TEACHER |
| GET | `/api/students/{id}` | 详情 | ADMIN、TEACHER |
| POST | `/api/students` | 新增 | ADMIN |
| PUT | `/api/students/{id}` | 修改 | ADMIN |
| DELETE | `/api/students/{id}` | 删除（级联删除其成绩） | ADMIN |
| POST | `/api/students/{id}/account` | 开通登录账号 | ADMIN |

### 课程
| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/courses?keyword=` | 列表（教师仅自己的） | 已登录 |
| POST | `/api/courses` | 新增 | ADMIN |
| PUT | `/api/courses/{id}` | 修改 | ADMIN |
| DELETE | `/api/courses/{id}` | 删除（有关联考试时拒绝） | ADMIN |

### 考试
| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/exams?courseId=` | 某课程的考试列表 | 已登录 |
| POST | `/api/exams` | 新增 | ADMIN、该课程 TEACHER |
| PUT | `/api/exams/{id}` | 修改 | ADMIN、该课程 TEACHER |
| DELETE | `/api/exams/{id}` | 删除（级联删除成绩） | ADMIN、该课程 TEACHER |

### 成绩
| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/scores?examId=&className=` | 全部学生名单（可按班级筛选）+ 已有分数 | ADMIN、该课程 TEACHER |
| POST | `/api/scores/batch` | 批量保存，存在则更新 | ADMIN、该课程 TEACHER |
| DELETE | `/api/scores/{id}` | 删除 | ADMIN、该课程 TEACHER |
| GET | `/api/scores/my?courseId=` | 本人成绩 | STUDENT |

### 用户（账号）
| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/users?role=&keyword=&page=&size=` | 账号列表 | ADMIN |
| POST | `/api/users` | 新建教师账号 | ADMIN |
| PUT | `/api/users/{id}/status` | 启用/禁用 | ADMIN |
| PUT | `/api/users/{id}/password` | 重置密码为 123456 | ADMIN |

### 分析
| 方法 | 路径 | 返回 | 权限 |
|---|---|---|---|
| GET | `/api/analysis/overview` | `{studentCount, courseCount, avgScore, passRate}` | ADMIN、TEACHER |
| GET | `/api/analysis/distribution?examId=` | `[{range, count}]`，分段 90-100/80-89/70-79/60-69/<60 | ADMIN、该课程 TEACHER |
| GET | `/api/analysis/trend?studentId=&courseId=` | `[{examName, examDate, percent}]` | ADMIN、TEACHER、本人 |
| GET | `/api/analysis/course-comparison` | `[{courseName, avgScore, passRate}]` | ADMIN、TEACHER（自己课程） |
| GET | `/api/analysis/failing?examId=&threshold=60` | `[{studentNo, name, score}]` | ADMIN、该课程 TEACHER |

## 7. 前端页面

| 页面 | 路由 | 角色 | 内容 |
|---|---|---|---|
| 登录 | `/login` | 公开 | 表单 + 登录后按角色跳转 |
| 仪表盘 | `/dashboard` | 全部 | 统计卡片 + 分布图 + 课程对比；学生端显示本人平均分与各课程总评 |
| 学生管理 | `/students` | ADMIN 可写，TEACHER 只读 | 搜索/分页/弹窗表单/开通账号 |
| 课程管理 | `/courses` | ADMIN | 表格 + 表单，分配教师 |
| 用户管理 | `/users` | ADMIN | 账号列表、新建教师、启停、重置密码 |
| 考试管理 | `/exams` | ADMIN / TEACHER | 按课程筛考试，新增/编辑/删除 |
| 成绩录入 | `/scores` | ADMIN / TEACHER | 选课程 → 选考试 → 名单内联输入 → 批量保存 |
| 成绩分析 | `/analysis` | ADMIN / TEACHER | Tab：分数段分布 / 课程对比 / 不及格预警（阈值可调） |
| 我的成绩 | `/my-scores` | STUDENT | 课程总评表 + 排名 + 趋势折线图 |

- 路由守卫：无 token → `/login`；角色不符 → 提示并回到 `/dashboard`。
- 图表统一用 `BaseChart` 组件封装 ECharts 的 init/resize/dispose。

## 8. 错误处理

后端：
- `BusinessException(code, message)`：400（业务校验）、404（不存在）、409（冲突）。
- `@RestControllerAdvice` 统一处理：校验异常返回字段级提示；`AccessDeniedException` → 403；`AuthenticationException` → 401；其他异常记录堆栈，对外"系统繁忙"。
- 边界校验：分数范围、满分 > 0、名称非空、唯一约束。

前端：
- Axios 拦截器附加 token；401 → 清 token 跳登录；其他错误 `ElMessage.error`。
- 表单 rules 前置校验，后端校验为最终防线。

## 9. 测试策略

后端（JUnit 5 + MockMvc，测试库 H2 MODE=MySQL，`./mvnw test` 无外部依赖）：
1. 登录成功/失败、无 token 401、角色不符 403
2. 教师越权访问他人课程 → 403
3. 学生查询他人成绩 → 403
4. 成绩越界（负数、超满分）→ 400
5. 重复录入覆盖更新
6. 分数段边界（59.9 / 60 / 89.9 / 90）、及格率、排名并列
7. 课程总评归一化（如 80/100 + 45/50 → 85.0）

按 TDD：先红后绿。前端：Vitest 覆盖路由守卫与工具函数，`npm run build` 作为烟雾测试，关键流程浏览器实测。

## 10. 初始数据与运行

- `CommandLineRunner` 初始化（仅当 `sys_user` 为空时执行）：
  - 管理员 `admin / 123456`
  - 教师 `teacher1 / 123456`、`teacher2 / 123456`
  - 一个班 8 名学生，每人开通账号（学号 / 123456）
  - 3 门课程、每门 3 次考试、成绩数据完整
- 运行步骤（写入 README）：
  1. `brew install mysql && brew services start mysql`
  2. 创建库与外键用户（或使用 root）
  3. 修改 `backend/src/main/resources/application.yml`（账号密码）
  4. `cd backend && ./mvnw spring-boot:run`
  5. `cd frontend && npm install && npm run dev`

## 11. 风险与取舍

- Vue 3 + Element Plus 与原始需求的 Element UI 存在版本差异，已与用户确认。
- JWT 无状态，注销后 token 到期前仍有效（课程项目可接受，不引入黑名单）。
- 学生-课程之间没有选课表，成绩录入按"课程学生名单"基于班级/全量学生展开；若需真实选课关系需增加 `enrollment` 表（本期不做）。
- 班级用字符串字段而非独立表，够用且减少 CRUD 页面（YAGNI）。
