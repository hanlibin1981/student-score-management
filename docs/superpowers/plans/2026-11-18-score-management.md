# 学生成绩管理系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一套前后端分离的学生成绩管理系统，支持管理员/教师/学生三角色登录、学生与课程管理、成绩录入与统计分析，并能在本机直接运行。

**Architecture:** Spring Boot 3.5 + Spring Security（JWT 无状态认证）+ MyBatis-Plus 提供 REST API；Vue 3 + Vite + Element Plus + ECharts 提供前端；MySQL 8 存储数据，测试用 H2 的 MySQL 兼容模式。前后端通过 `/api` 约定通信，Vite 开发代理转发。

**Tech Stack:** Java 17（运行环境 Java 20）、Spring Boot 3.5.x、Spring Security、jjwt 0.12.6、MyBatis-Plus 3.5.7、MySQL 8、H2（test）、Vue 3、Vite、Element Plus、Pinia、Vue Router 4、Axios、ECharts、Vitest。

**设计文档：** `docs/superpowers/specs/2026-11-18-score-management-design.md`

---

## 环境约定

- 工作目录：`/Users/mac/workspace/score`（已 git init，已提交设计文档）
- 后端目录：`backend/`，包名 `com.score`，端口 8080
- 前端目录：`frontend/`，端口 5173
- 统一响应：`{ code: 0, message: "ok", data: ... }`；分页：`{ list, total, page, size }`
- 错误码即 HTTP 状态码：400 业务校验 / 401 未登录 / 403 无权限 / 404 不存在 / 409 冲突
- 每次提交前确认 `./mvnw test` 或 `npm run build` 通过

## 文件结构

```
backend/
├── pom.xml                                  Spring Boot 3.5 + MyBatis-Plus + jjwt
└── src/
    ├── main/java/com/score/
    │   ├── ScoreApplication.java            @MapperScan
    │   ├── common/                          Result、PageResult、BusinessException、GlobalExceptionHandler、Role
    │   ├── config/                          SecurityConfig、MybatisPlusConfig、DataInitializer
    │   ├── security/                        AuthUser、JwtUtil、JwtAuthenticationFilter、SecurityUtils
    │   ├── entity/                          User、Student、Course、Exam、Score
    │   ├── mapper/                          UserMapper、StudentMapper、CourseMapper、ExamMapper、ScoreMapper
    │   ├── dto/                             入参对象（含校验注解）
    │   ├── vo/                              出参对象（分析结果、CourseVO、UserVO 等）
    │   ├── service/                         AuthService、StudentService、CourseService、ExamService、ScoreService、AnalysisService、UserService
    │   ├── controller/                      对应 7 个 Controller
    │   └── util/StatsCalculator.java        排名计算
    ├── main/resources/
    │   ├── application.yml                  MySQL + JWT 配置
    │   ├── schema.sql                       建表脚本（IF NOT EXISTS）
    │   └── mapper/ScoreMapper.xml           统计 SQL
    └── test/
        ├── java/com/score/support/BaseIntegrationTest.java
        ├── java/com/score/*IT.java          集成测试
        ├── java/com/score/util/StatsCalculatorTest.java
        └── resources/application-test.yml + schema-test.sql
frontend/
├── package.json / vite.config.js
└── src/
    ├── main.js / App.vue
    ├── api/request.js、api/index.js
    ├── stores/auth.js
    ├── router/index.js
    ├── utils/permission.js（可单测）
    ├── layouts/MainLayout.vue
    ├── components/BaseChart.vue
    └── views/ Login、Dashboard、Students、Courses、Users、Exams、Scores、Analysis、MyScores
```

---

### Task 0: 准备 MySQL 8

**Files:** 无（环境准备）

- [ ] **Step 1: 安装并启动 MySQL**

```bash
brew install mysql
brew services start mysql
```

- [ ] **Step 2: 建库**

```bash
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS score_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -e "SHOW DATABASES;"
```

Expected: 输出中包含 `score_db`。Homebrew 安装的 MySQL root 默认无密码；若已有密码，后续在 `application.yml` 中填写。

- [ ] **Step 3: 记录连接信息**

本计划默认使用 `root` / 空密码 / 端口 3306。如果与你机器不符，在 Task 1 的 `application.yml` 中同步修改。

---

### Task 1: 后端脚手架与依赖

**Files:**
- Create: `backend/`（由 start.spring.io 生成）
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/score/ScoreApplication.java`

- [ ] **Step 1: 查看可用的 Spring Boot 3.x 版本**

```bash
curl -s https://start.spring.io/metadata/client | python3 -c "import sys,json; d=json.load(sys.stdin); print([v['id'] for v in d['bootVersion']['values']])"
```

Expected: 输出版本列表。选择其中最新的 **3.x** 版本（不要选 4.x，MyBatis-Plus 3.5.7 只兼容 Spring Boot 3；若列表里没有 3.x，手动把 pom 的 parent 版本改为 `3.5.0` 可用版本）。

- [ ] **Step 2: 生成后端骨架**

```bash
cd /Users/mac/workspace/score
BOOT_VERSION=3.5.0   # 若 Step 1 的输出里没有该版本，改成列表中最新的 3.x
curl -s https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=$BOOT_VERSION \
  -d javaVersion=17 -d groupId=com.score -d artifactId=backend \
  -d name=score-backend -d packageName=com.score \
  -d dependencies=web,security,validation,mysql,h2,lombok \
  -o /tmp/backend.zip
mkdir -p backend && unzip -q -o /tmp/backend.zip -d backend
chmod +x backend/mvnw
ls backend
```

Expected: 看到 `pom.xml`、`mvnw`、`src`。若 curl 返回的是 HTML/错误，先把 `BOOT_VERSION` 改为 Step 1 列表中的实际值重试。

- [ ] **Step 3: 在 pom.xml 中加入 MyBatis-Plus 与 jjwt**

在 `<dependencies>` 内追加（版本号固定）：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.7</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

同时把自动生成的 `h2` 依赖的 `<scope>` 改为 `test`（只用于测试）。

在 `<build><plugins>` 中追加 Surefire 配置，让 `*IT` 命名的集成测试也会被 `./mvnw test` 执行（Surefire 默认只识别 `*Test`）：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
</plugin>
```

- [ ] **Step 4: 写 application.yml**

`backend/src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/score_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8
    username: root
    password: ""
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true

app:
  jwt:
    secret: "score-management-system-jwt-secret-key-2026-please-change"
    expiration-ms: 86400000
```

- [ ] **Step 5: 给启动类加 MapperScan**

`backend/src/main/java/com/score/ScoreApplication.java`：

```java
package com.score;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.score.mapper")
public class ScoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScoreApplication.class, args);
    }
}
```

- [ ] **Step 6: 验证编译**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q -DskipTests compile
```

Expected: 首次运行会下载 Maven 与依赖（可能几分钟），最终退出码 0。

- [ ] **Step 7: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "chore: scaffold spring boot backend with mybatis-plus and jwt"
```

---

### Task 2: 建表脚本、测试配置与公共层

**Files:**
- Create: `backend/src/main/resources/schema.sql`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/resources/schema-test.sql`
- Create: `backend/src/main/java/com/score/common/{Result,PageResult,BusinessException,GlobalExceptionHandler,Role}.java`
- Create: `backend/src/test/java/com/score/ApplicationContextTest.java`

- [ ] **Step 1: 写 MySQL 建表脚本**

`backend/src/main/resources/schema.sql`：

```sql
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    role VARCHAR(20) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_no VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(10),
    class_name VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    user_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_student_class (class_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    credit DECIMAL(3,1),
    semester VARCHAR(20),
    teacher_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_course_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    exam_date DATE,
    full_score INT NOT NULL DEFAULT 100,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exam_course_name (course_id, name),
    KEY idx_exam_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    exam_id BIGINT NOT NULL,
    score DECIMAL(5,1) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_score_student_exam (student_id, exam_id),
    KEY idx_score_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 写测试配置与 H2 建表脚本**

`backend/src/test/resources/application-test.yml`：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:score_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-test.sql

app:
  jwt:
    secret: "test-secret-key-for-score-system-0123456789"
    expiration-ms: 86400000
```

`backend/src/test/resources/schema-test.sql`（同 MySQL 版本，去掉 ENGINE/CHARSET 与 KEY 子句）：

```sql
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    role VARCHAR(20) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_no VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(10),
    class_name VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    user_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    credit DECIMAL(3,1),
    semester VARCHAR(20),
    teacher_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    exam_date DATE,
    full_score INT NOT NULL DEFAULT 100,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (course_id, name)
);

CREATE TABLE IF NOT EXISTS score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    exam_id BIGINT NOT NULL,
    score DECIMAL(5,1) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (student_id, exam_id)
);
```

- [ ] **Step 3: 写公共层**

`common/Role.java`：

```java
package com.score.common;

public final class Role {
    public static final String ADMIN = "ADMIN";
    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";

    private Role() {
    }
}
```

`common/Result.java`：

```java
package com.score.common;

public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
```

`common/PageResult.java`：

```java
package com.score.common;

import java.util.List;

public record PageResult<T>(List<T> list, long total, long page, long size) {
}
```

`common/BusinessException.java`：

```java
package com.score.common;

public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
```

`common/GlobalExceptionHandler.java`：

```java
package com.score.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = switch (e.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(Result.fail(400, e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicate(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.fail(409, "数据已存在"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(403, "无权限执行该操作"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(500, "系统繁忙，请稍后重试"));
    }
}
```

- [ ] **Step 4: 写上下文冒烟测试**

`backend/src/test/java/com/score/ApplicationContextTest.java`：

```java
package com.score;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: 运行测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=ApplicationContextTest
```

Expected: PASS（本任务是基础设施，环境能起来即可；若失败先检查 `application-test.yml` 缩进与 H2 依赖 scope）。

- [ ] **Step 6: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add schema, test profile and common response layer"
```

---

### Task 3: 实体、Mapper 与 MyBatis-Plus 配置

**Files:**
- Create: `backend/src/main/java/com/score/entity/{User,Student,Course,Exam,Score}.java`
- Create: `backend/src/main/java/com/score/mapper/{User,Student,Course,Exam,Score}Mapper.java`
- Create: `backend/src/main/java/com/score/config/MybatisPlusConfig.java`
- Test: `backend/src/test/java/com/score/MapperWiringTest.java`

- [ ] **Step 1: 写 Mapper 连通性测试（先失败）**

`backend/src/test/java/com/score/MapperWiringTest.java`：

```java
package com.score;

import com.score.entity.User;
import com.score.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MapperWiringTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertsAndReadsBackAUser() {
        User user = new User();
        user.setUsername("mapper_test");
        user.setPassword("x");
        user.setRealName("映射测试");
        user.setRole("ADMIN");
        user.setEnabled(true);

        userMapper.insert(user);
        User loaded = userMapper.selectById(user.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getUsername()).isEqualTo("mapper_test");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=MapperWiringTest
```

Expected: 编译失败，`User` / `UserMapper` 不存在。

- [ ] **Step 3: 写实体**

`entity/User.java`：

```java
package com.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String role;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
```

`entity/Student.java`：

```java
package com.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String studentNo;
    private String name;
    private String gender;
    private String className;
    private String phone;
    private String email;
    private Long userId;
    private LocalDateTime createdAt;
}
```

`entity/Course.java`：

```java
package com.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String courseCode;
    private String name;
    private BigDecimal credit;
    private String semester;
    private Long teacherId;
    private LocalDateTime createdAt;
}
```

`entity/Exam.java`：

```java
package com.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("exam")
public class Exam {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String name;
    private LocalDate examDate;
    private Integer fullScore;
    private LocalDateTime createdAt;
}
```

`entity/Score.java`：

```java
package com.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("score")
public class Score {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long examId;
    private BigDecimal score;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 写 Mapper 接口**

`mapper/UserMapper.java`：

```java
package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.User;

public interface UserMapper extends BaseMapper<User> {
}
```

`mapper/StudentMapper.java`：

```java
package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.Student;

public interface StudentMapper extends BaseMapper<Student> {
}
```

`mapper/CourseMapper.java`：

```java
package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.Course;

public interface CourseMapper extends BaseMapper<Course> {
}
```

`mapper/ExamMapper.java`：

```java
package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.Exam;

public interface ExamMapper extends BaseMapper<Exam> {
}
```

`mapper/ScoreMapper.java`（统计方法在 Task 9 追加）：

```java
package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.Score;

public interface ScoreMapper extends BaseMapper<Score> {
}
```

- [ ] **Step 5: 写分页插件配置**

`config/MybatisPlusConfig.java`：

```java
package com.score.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 6: 运行测试（预期通过）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=MapperWiringTest
```

Expected: PASS。若报 `Invalid bound statement`，检查启动类上的 `@MapperScan("com.score.mapper")` 是否生效。

- [ ] **Step 7: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add entities, mappers and pagination config"
```

---

### Task 4: JWT 认证、登录接口与种子数据

**Files:**
- Create: `backend/src/main/java/com/score/security/{AuthUser,JwtUtil,JwtAuthenticationFilter,SecurityUtils}.java`
- Create: `backend/src/main/java/com/score/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/score/dto/{LoginRequest,PasswordChangeRequest}.java`
- Create: `backend/src/main/java/com/score/vo/{UserVO,LoginResponse}.java`
- Create: `backend/src/main/java/com/score/service/AuthService.java`
- Create: `backend/src/main/java/com/score/controller/AuthController.java`
- Create: `backend/src/main/java/com/score/config/DataInitializer.java`
- Test: `backend/src/test/java/com/score/support/BaseIntegrationTest.java`
- Test: `backend/src/test/java/com/score/AuthIT.java`

- [ ] **Step 1: 写认证集成测试（先失败）**

`backend/src/test/java/com/score/support/BaseIntegrationTest.java`：

```java
package com.score.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import com.score.mapper.StudentMapper;
import com.score.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserMapper userMapper;
    @Autowired protected StudentMapper studentMapper;
    @Autowired protected CourseMapper courseMapper;
    @Autowired protected ExamMapper examMapper;
    @Autowired protected ScoreMapper scoreMapper;

    protected String token(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    protected String adminToken() throws Exception {
        return token("admin");
    }

    protected String teacher1Token() throws Exception {
        return token("teacher1");
    }

    protected String teacher2Token() throws Exception {
        return token("teacher2");
    }

    protected String studentToken() throws Exception {
        return token("2026001");
    }
}
```

`backend/src/test/java/com/score/AuthIT.java`：

```java
package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.User;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends BaseIntegrationTest {

    @Test
    void loginSucceedsWithValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        User update = new User();
        update.setId(admin.getId());
        update.setEnabled(false);
        userMapper.updateById(update);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
    }

    @Test
    void protectedEndpointRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void currentUserEndpointReturnsProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("teacher1"))
                .andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=AuthIT
```

Expected: 编译失败（`AuthController` 等不存在）。

- [ ] **Step 3: 写 security 包**

`security/AuthUser.java`：

```java
package com.score.security;

public record AuthUser(Long id, String username, String role) {
}
```

`security/JwtUtil.java`：

```java
package com.score.security;

import com.score.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generate(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }
}
```

`security/JwtAuthenticationFilter.java`：

```java
package com.score.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthUser user = jwtUtil.parse(header.substring(7));
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
                var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

`security/SecurityUtils.java`：

```java
package com.score.security;

import com.score.common.BusinessException;
import com.score.common.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }

    public static Long currentUserId() {
        return current().id();
    }

    public static boolean isAdmin() {
        return Role.ADMIN.equals(current().role());
    }

    public static boolean isStudent() {
        return Role.STUDENT.equals(current().role());
    }
}
```

- [ ] **Step 4: 写认证 DTO 与 VO**

`dto/LoginRequest.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

`dto/PasswordChangeRequest.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需为 6-32 位")
    private String newPassword;
}
```

`vo/UserVO.java`：

```java
package com.score.vo;

import com.score.entity.User;

public record UserVO(Long id, String username, String realName, String role, Boolean enabled) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), user.getEnabled());
    }
}
```

`vo/LoginResponse.java`：

```java
package com.score.vo;

public record LoginResponse(String token, UserVO user) {
}
```

- [ ] **Step 5: 写 SecurityConfig**

`config/SecurityConfig.java`：

```java
package com.score.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.score.common.Result;
import com.score.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无权限执行该操作")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(status, message)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

- [ ] **Step 6: 写 AuthService 与 AuthController**

`service/AuthService.java`：

```java
package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.dto.LoginRequest;
import com.score.dto.PasswordChangeRequest;
import com.score.entity.User;
import com.score.mapper.UserMapper;
import com.score.security.JwtUtil;
import com.score.security.SecurityUtils;
import com.score.vo.LoginResponse;
import com.score.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw BusinessException.forbidden("账号已被禁用");
        }
        return new LoginResponse(jwtUtil.generate(user), UserVO.from(user));
    }

    public UserVO me() {
        User user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return UserVO.from(user);
    }

    public void changePassword(PasswordChangeRequest request) {
        User user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(update);
    }
}
```

`controller/AuthController.java`：

```java
package com.score.controller;

import com.score.common.Result;
import com.score.dto.LoginRequest;
import com.score.dto.PasswordChangeRequest;
import com.score.service.AuthService;
import com.score.vo.LoginResponse;
import com.score.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.me());
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }
}
```

- [ ] **Step 7: 写种子数据初始化**

`config/DataInitializer.java`：

```java
package com.score.config;

import com.score.common.Role;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.entity.Student;
import com.score.entity.User;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import com.score.mapper.StudentMapper;
import com.score.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        log.info("初始化演示数据...");

        User admin = createUser("admin", "系统管理员", Role.ADMIN);
        User teacher1 = createUser("teacher1", "张老师", Role.TEACHER);
        User teacher2 = createUser("teacher2", "李老师", Role.TEACHER);

        List<Student> students = new ArrayList<>();
        String[] names = {"张伟", "王芳", "李娜", "刘洋", "陈静", "杨磊", "赵敏", "周杰"};
        for (int i = 0; i < names.length; i++) {
            String studentNo = "2026" + String.format("%03d", i + 1);
            User account = createUser(studentNo, names[i], Role.STUDENT);

            Student student = new Student();
            student.setStudentNo(studentNo);
            student.setName(names[i]);
            student.setGender(i % 2 == 0 ? "男" : "女");
            student.setClassName("软件工程2601");
            student.setPhone("1380000" + String.format("%04d", i + 1));
            student.setEmail(studentNo + "@example.com");
            student.setUserId(account.getId());
            studentMapper.insert(student);
            students.add(student);
        }

        List<Course> courses = List.of(
                createCourse("CS101", "高等数学", new BigDecimal("4.0"), "2026-2027-1", teacher1.getId()),
                createCourse("CS102", "数据结构", new BigDecimal("3.5"), "2026-2027-1", teacher1.getId()),
                createCourse("EN101", "大学英语", new BigDecimal("3.0"), "2026-2027-1", teacher2.getId()));

        String[] examNames = {"第一次月考", "期中考试", "期末考试"};
        LocalDate[] examDates = {
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2027, 1, 10)};

        for (int c = 0; c < courses.size(); c++) {
            for (int e = 0; e < examNames.length; e++) {
                Exam exam = new Exam();
                exam.setCourseId(courses.get(c).getId());
                exam.setName(examNames[e]);
                exam.setExamDate(examDates[e]);
                exam.setFullScore(100);
                examMapper.insert(exam);

                for (int i = 0; i < students.size(); i++) {
                    Score score = new Score();
                    score.setStudentId(students.get(i).getId());
                    score.setExamId(exam.getId());
                    score.setScore(BigDecimal.valueOf(55 + ((i * 7 + c * 13 + e * 17) % 41)));
                    scoreMapper.insert(score);
                }
            }
        }
        log.info("演示数据初始化完成：{} 个用户，{} 名学生，{} 门课程", userMapper.selectCount(null),
                students.size(), courses.size());
    }

    private User createUser(String username, String realName, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRealName(realName);
        user.setRole(role);
        user.setEnabled(true);
        userMapper.insert(user);
        return user;
    }

    private Course createCourse(String code, String name, BigDecimal credit, String semester, Long teacherId) {
        Course course = new Course();
        course.setCourseCode(code);
        course.setName(name);
        course.setCredit(credit);
        course.setSemester(semester);
        course.setTeacherId(teacherId);
        courseMapper.insert(course);
        return course;
    }
}
```

- [ ] **Step 8: 运行认证测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=AuthIT
```

Expected: 全部 PASS（6 个测试）。若 `disabledAccountCannotLogin` 失败，检查 `AuthService` 是否在密码匹配之后才判断 enabled。

- [ ] **Step 9: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add jwt authentication, login api and seed data"
```

---

### Task 5: 学生信息管理与账号开通

**Files:**
- Create: `backend/src/main/java/com/score/dto/StudentRequest.java`
- Create: `backend/src/main/java/com/score/vo/StudentVO.java`
- Create: `backend/src/main/java/com/score/service/StudentService.java`
- Create: `backend/src/main/java/com/score/controller/StudentController.java`
- Test: `backend/src/test/java/com/score/StudentIT.java`

- [ ] **Step 1: 写学生管理集成测试（先失败）**

`backend/src/test/java/com/score/StudentIT.java`：

```java
package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.Score;
import com.score.entity.Student;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentIT extends BaseIntegrationTest {

    @Test
    void adminCanCreateStudent() throws Exception {
        String body = """
                {"studentNo":"2026999","name":"测试生","gender":"男","className":"软件工程2601",
                 "phone":"13900000000","email":"test@example.com"}""";
        mockMvc.perform(post("/api/students").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentNo").value("2026999"))
                .andExpect(jsonPath("$.data.hasAccount").value(false));
    }

    @Test
    void duplicateStudentNoRejected() throws Exception {
        String body = """
                {"studentNo":"2026001","name":"重复学号","className":"软件工程2601"}""";
        mockMvc.perform(post("/api/students").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherCanListStudents() throws Exception {
        mockMvc.perform(get("/api/students").header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(8));
    }

    @Test
    void teacherCannotCreateStudent() throws Exception {
        String body = """
                {"studentNo":"2026777","name":"越权创建"}""";
        mockMvc.perform(post("/api/students").header("Authorization", "Bearer " + teacher1Token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotListStudents() throws Exception {
        mockMvc.perform(get("/api/students").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanOpenAccountAndStudentCanLogin() throws Exception {
        String body = """
                {"studentNo":"2026888","name":"新同学","gender":"女","className":"软件工程2601"}""";
        String response = mockMvc.perform(post("/api/students")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long studentId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/students/" + studentId + "/account")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAccount").value(true));

        assertThat(token("2026888")).isNotBlank();
    }

    @Test
    void deleteStudentCascadesScores() throws Exception {
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, "2026008"));
        long scoreCount = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, student.getId()));
        assertThat(scoreCount).isGreaterThan(0);

        mockMvc.perform(delete("/api/students/" + student.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        assertThat(scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, student.getId()))).isZero();
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=StudentIT
```

Expected: 编译失败（`StudentService` 等不存在）。

- [ ] **Step 3: 写 DTO / VO**

`dto/StudentRequest.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StudentRequest {
    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String gender;
    private String className;
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;
}
```

`vo/StudentVO.java`：

```java
package com.score.vo;

import com.score.entity.Student;

public record StudentVO(Long id, String studentNo, String name, String gender, String className,
                        String phone, String email, boolean hasAccount) {

    public static StudentVO from(Student student) {
        return new StudentVO(student.getId(), student.getStudentNo(), student.getName(),
                student.getGender(), student.getClassName(), student.getPhone(), student.getEmail(),
                student.getUserId() != null);
    }
}
```

- [ ] **Step 4: 写 StudentService**

`service/StudentService.java`：

```java
package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.score.common.BusinessException;
import com.score.common.PageResult;
import com.score.common.Role;
import com.score.dto.StudentRequest;
import com.score.entity.Score;
import com.score.entity.Student;
import com.score.entity.User;
import com.score.mapper.ScoreMapper;
import com.score.mapper.StudentMapper;
import com.score.mapper.UserMapper;
import com.score.vo.StudentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    public static final String DEFAULT_PASSWORD = "123456";

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ScoreMapper scoreMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<StudentVO> page(String keyword, String className, long page, long size) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Student::getStudentNo, keyword)
                        .or()
                        .like(Student::getName, keyword))
                .eq(StringUtils.hasText(className), Student::getClassName, className)
                .orderByAsc(Student::getStudentNo);
        Page<Student> result = studentMapper.selectPage(new Page<>(page, size), wrapper);
        List<StudentVO> list = result.getRecords().stream().map(StudentVO::from).toList();
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public StudentVO get(Long id) {
        return StudentVO.from(requireStudent(id));
    }

    public StudentVO create(StudentRequest request) {
        long exists = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, request.getStudentNo()));
        if (exists > 0) {
            throw BusinessException.conflict("学号已存在");
        }
        Student student = new Student();
        apply(student, request);
        studentMapper.insert(student);
        return StudentVO.from(student);
    }

    public StudentVO update(Long id, StudentRequest request) {
        Student student = requireStudent(id);
        long exists = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, request.getStudentNo())
                .ne(Student::getId, id));
        if (exists > 0) {
            throw BusinessException.conflict("学号已存在");
        }
        Student update = new Student();
        update.setId(student.getId());
        apply(update, request);
        studentMapper.updateById(update);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        Student student = requireStudent(id);
        scoreMapper.delete(new LambdaQueryWrapper<Score>().eq(Score::getStudentId, id));
        if (student.getUserId() != null) {
            userMapper.deleteById(student.getUserId());
        }
        studentMapper.deleteById(id);
    }

    @Transactional
    public StudentVO openAccount(Long id) {
        Student student = requireStudent(id);
        if (student.getUserId() != null) {
            throw BusinessException.conflict("该学生已开通账号");
        }
        long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, student.getStudentNo()));
        if (exists > 0) {
            throw BusinessException.conflict("用户名与学号冲突");
        }
        User account = new User();
        account.setUsername(student.getStudentNo());
        account.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        account.setRealName(student.getName());
        account.setRole(Role.STUDENT);
        account.setEnabled(true);
        userMapper.insert(account);

        Student update = new Student();
        update.setId(student.getId());
        update.setUserId(account.getId());
        studentMapper.updateById(update);
        return get(id);
    }

    private Student requireStudent(Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw BusinessException.notFound("学生不存在");
        }
        return student;
    }

    private void apply(Student student, StudentRequest request) {
        student.setStudentNo(request.getStudentNo());
        student.setName(request.getName());
        student.setGender(request.getGender());
        student.setClassName(request.getClassName());
        student.setPhone(request.getPhone());
        student.setEmail(request.getEmail());
    }
}
```

- [ ] **Step 5: 写 StudentController**

`controller/StudentController.java`：

```java
package com.score.controller;

import com.score.common.PageResult;
import com.score.common.Result;
import com.score.dto.StudentRequest;
import com.score.service.StudentService;
import com.score.vo.StudentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<PageResult<StudentVO>> page(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String className,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(studentService.page(keyword, className, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<StudentVO> get(@PathVariable Long id) {
        return Result.ok(studentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StudentVO> create(@Valid @RequestBody StudentRequest request) {
        return Result.ok(studentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StudentVO> update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return Result.ok(studentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/account")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StudentVO> openAccount(@PathVariable Long id) {
        return Result.ok(studentService.openAccount(id));
    }
}
```

- [ ] **Step 6: 运行测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=StudentIT
```

Expected: 7 个测试全部 PASS。

- [ ] **Step 7: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add student management and account opening"
```

---

### Task 6: 课程管理（含教师归属校验）

**Files:**
- Create: `backend/src/main/java/com/score/dto/CourseRequest.java`
- Create: `backend/src/main/java/com/score/vo/CourseVO.java`
- Create: `backend/src/main/java/com/score/service/CourseService.java`
- Create: `backend/src/main/java/com/score/controller/CourseController.java`
- Test: `backend/src/test/java/com/score/CourseIT.java`

- [ ] **Step 1: 写课程集成测试（先失败）**

`backend/src/test/java/com/score/CourseIT.java`：

```java
package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.Course;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseIT extends BaseIntegrationTest {

    @Test
    void teacherOnlySeesOwnCourses() throws Exception {
        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].teacherName").value("张老师"));
    }

    @Test
    void studentCanListCourses() throws Exception {
        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void adminCanCreateCourse() throws Exception {
        String body = """
                {"courseCode":"CS201","name":"操作系统","credit":3.5,"semester":"2026-2027-2"}""";
        mockMvc.perform(post("/api/courses").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseCode").value("CS201"));
    }

    @Test
    void teacherCannotCreateCourse() throws Exception {
        String body = """
                {"courseCode":"CS202","name":"计算机网络"}""";
        mockMvc.perform(post("/api/courses").header("Authorization", "Bearer " + teacher1Token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCourseWithExamsRejected() throws Exception {
        Course math = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getName, "高等数学"));
        mockMvc.perform(delete("/api/courses/" + math.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=CourseIT
```

Expected: 编译失败（`CourseService` 不存在）。

- [ ] **Step 3: 写 DTO / VO**

`dto/CourseRequest.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseRequest {
    @NotBlank(message = "课程编号不能为空")
    private String courseCode;

    @NotBlank(message = "课程名称不能为空")
    private String name;

    private BigDecimal credit;
    private String semester;
    private Long teacherId;
}
```

`vo/CourseVO.java`：

```java
package com.score.vo;

import com.score.entity.Course;

import java.math.BigDecimal;

public record CourseVO(Long id, String courseCode, String name, BigDecimal credit,
                       String semester, Long teacherId, String teacherName) {

    public static CourseVO from(Course course, String teacherName) {
        return new CourseVO(course.getId(), course.getCourseCode(), course.getName(),
                course.getCredit(), course.getSemester(), course.getTeacherId(), teacherName);
    }
}
```

- [ ] **Step 4: 写 CourseService**

`service/CourseService.java`：

```java
package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.common.Role;
import com.score.dto.CourseRequest;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.User;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.UserMapper;
import com.score.security.SecurityUtils;
import com.score.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final ExamMapper examMapper;
    private final UserMapper userMapper;

    public List<CourseVO> list(String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .like(StringUtils.hasText(keyword), Course::getName, keyword)
                .orderByAsc(Course::getId);
        if (Role.TEACHER.equals(SecurityUtils.current().role())) {
            wrapper.eq(Course::getTeacherId, SecurityUtils.currentUserId());
        }
        List<Course> courses = courseMapper.selectList(wrapper);

        Set<Long> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> teacherNames = teacherIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName));

        return courses.stream()
                .map(course -> CourseVO.from(course, teacherNames.get(course.getTeacherId())))
                .toList();
    }

    public CourseVO create(CourseRequest request) {
        long exists = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                .eq(Course::getCourseCode, request.getCourseCode()));
        if (exists > 0) {
            throw BusinessException.conflict("课程编号已存在");
        }
        Course course = new Course();
        apply(course, request);
        courseMapper.insert(course);
        return detail(course);
    }

    public CourseVO update(Long id, CourseRequest request) {
        requireCourse(id);
        long exists = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                .eq(Course::getCourseCode, request.getCourseCode())
                .ne(Course::getId, id));
        if (exists > 0) {
            throw BusinessException.conflict("课程编号已存在");
        }
        Course update = new Course();
        update.setId(id);
        apply(update, request);
        courseMapper.updateById(update);
        return detail(requireCourse(id));
    }

    public void delete(Long id) {
        requireCourse(id);
        long examCount = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, id));
        if (examCount > 0) {
            throw BusinessException.conflict("该课程已有考试记录，不能删除");
        }
        courseMapper.deleteById(id);
    }

    /**
     * 管理员可操作任意课程；教师只能操作自己的课程。
     */
    public Course requireAccessibleCourse(Long courseId) {
        Course course = requireCourse(courseId);
        if (!SecurityUtils.isAdmin()
                && !Objects.equals(course.getTeacherId(), SecurityUtils.currentUserId())) {
            throw BusinessException.forbidden("无权操作该课程");
        }
        return course;
    }

    private Course requireCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw BusinessException.notFound("课程不存在");
        }
        return course;
    }

    private CourseVO detail(Course course) {
        String teacherName = null;
        if (course.getTeacherId() != null) {
            User teacher = userMapper.selectById(course.getTeacherId());
            teacherName = teacher == null ? null : teacher.getRealName();
        }
        return CourseVO.from(course, teacherName);
    }

    private void apply(Course course, CourseRequest request) {
        course.setCourseCode(request.getCourseCode());
        course.setName(request.getName());
        course.setCredit(request.getCredit());
        course.setSemester(request.getSemester());
        course.setTeacherId(request.getTeacherId());
    }
}
```

- [ ] **Step 5: 写 CourseController**

`controller/CourseController.java`：

```java
package com.score.controller;

import com.score.common.Result;
import com.score.dto.CourseRequest;
import com.score.service.CourseService;
import com.score.vo.CourseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public Result<List<CourseVO>> list(@RequestParam(defaultValue = "") String keyword) {
        return Result.ok(courseService.list(keyword));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CourseVO> create(@Valid @RequestBody CourseRequest request) {
        return Result.ok(courseService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CourseVO> update(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return Result.ok(courseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 6: 运行测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=CourseIT
```

Expected: 5 个测试全部 PASS。

- [ ] **Step 7: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add course management with teacher ownership checks"
```

---

### Task 7: 考试管理

**Files:**
- Create: `backend/src/main/java/com/score/dto/ExamRequest.java`
- Create: `backend/src/main/java/com/score/service/ExamService.java`
- Create: `backend/src/main/java/com/score/controller/ExamController.java`
- Test: `backend/src/test/java/com/score/ExamIT.java`

- [ ] **Step 1: 写考试集成测试（先失败）**

`backend/src/test/java/com/score/ExamIT.java`：

```java
package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamIT extends BaseIntegrationTest {

    private Long courseId(String name) {
        return courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getName, name)).getId();
    }

    @Test
    void teacherCreatesExamOnOwnCourse() throws Exception {
        String body = """
                {"courseId":%d,"name":"补考","examDate":"2027-03-01","fullScore":100}"""
                .formatted(courseId("高等数学"));
        mockMvc.perform(post("/api/exams").header("Authorization", "Bearer " + teacher1Token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("补考"));
    }

    @Test
    void teacherCannotCreateExamOnOthersCourse() throws Exception {
        String body = """
                {"courseId":%d,"name":"越权考试","fullScore":100}"""
                .formatted(courseId("高等数学"));
        mockMvc.perform(post("/api/exams").header("Authorization", "Bearer " + teacher2Token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权操作该课程"));
    }

    @Test
    void duplicateExamNameRejected() throws Exception {
        String body = """
                {"courseId":%d,"name":"期中考试","fullScore":100}"""
                .formatted(courseId("高等数学"));
        mockMvc.perform(post("/api/exams").header("Authorization", "Bearer " + teacher1Token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void studentCannotCreateExam() throws Exception {
        String body = """
                {"courseId":%d,"name":"学生考试","fullScore":100}"""
                .formatted(courseId("高等数学"));
        mockMvc.perform(post("/api/exams").header("Authorization", "Bearer " + studentToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteExamCascadesScores() throws Exception {
        Exam exam = examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, courseId("高等数学"))
                .eq(Exam::getName, "期中考试"));
        mockMvc.perform(delete("/api/exams/" + exam.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        assertThat(scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, exam.getId()))).isZero();
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=ExamIT
```

Expected: 编译失败（`ExamService` 不存在）。

- [ ] **Step 3: 写 ExamRequest**

`dto/ExamRequest.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamRequest {
    @NotNull(message = "请选择课程")
    private Long courseId;

    @NotBlank(message = "考试名称不能为空")
    private String name;

    private LocalDate examDate;

    @NotNull(message = "满分不能为空")
    @Min(value = 1, message = "满分必须大于 0")
    private Integer fullScore;
}
```

- [ ] **Step 4: 写 ExamService**

`service/ExamService.java`：

```java
package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.dto.ExamRequest;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamMapper examMapper;
    private final CourseMapper courseMapper;
    private final ScoreMapper scoreMapper;
    private final CourseService courseService;

    public List<Exam> listByCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw BusinessException.notFound("课程不存在");
        }
        return examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, courseId)
                .orderByAsc(Exam::getExamDate)
                .orderByAsc(Exam::getId));
    }

    public Exam create(ExamRequest request) {
        courseService.requireAccessibleCourse(request.getCourseId());
        long exists = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, request.getCourseId())
                .eq(Exam::getName, request.getName()));
        if (exists > 0) {
            throw BusinessException.conflict("该课程下已存在同名考试");
        }
        Exam exam = new Exam();
        exam.setCourseId(request.getCourseId());
        apply(exam, request);
        examMapper.insert(exam);
        return exam;
    }

    public Exam update(Long id, ExamRequest request) {
        Exam exam = requireExam(id);
        courseService.requireAccessibleCourse(exam.getCourseId());
        long exists = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, exam.getCourseId())
                .eq(Exam::getName, request.getName())
                .ne(Exam::getId, id));
        if (exists > 0) {
            throw BusinessException.conflict("该课程下已存在同名考试");
        }
        Exam update = new Exam();
        update.setId(id);
        apply(update, request);
        examMapper.updateById(update);
        return examMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        Exam exam = requireExam(id);
        courseService.requireAccessibleCourse(exam.getCourseId());
        scoreMapper.delete(new LambdaQueryWrapper<Score>().eq(Score::getExamId, id));
        examMapper.deleteById(id);
    }

    public Exam requireExam(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw BusinessException.notFound("考试不存在");
        }
        return exam;
    }

    private void apply(Exam exam, ExamRequest request) {
        exam.setName(request.getName());
        exam.setExamDate(request.getExamDate());
        exam.setFullScore(request.getFullScore());
    }
}
```

- [ ] **Step 5: 写 ExamController**

`controller/ExamController.java`：

```java
package com.score.controller;

import com.score.common.Result;
import com.score.dto.ExamRequest;
import com.score.entity.Exam;
import com.score.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @GetMapping
    public Result<List<Exam>> listByCourse(@RequestParam Long courseId) {
        return Result.ok(examService.listByCourse(courseId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Exam> create(@Valid @RequestBody ExamRequest request) {
        return Result.ok(examService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Exam> update(@PathVariable Long id, @Valid @RequestBody ExamRequest request) {
        return Result.ok(examService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 6: 运行测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=ExamIT
```

Expected: 5 个测试全部 PASS。

- [ ] **Step 7: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add exam management with ownership checks"
```

---

### Task 8: 成绩录入与个人成绩查询

**Files:**
- Create: `backend/src/main/java/com/score/dto/{ScoreItem,ScoreBatchRequest}.java`
- Create: `backend/src/main/java/com/score/vo/{ExamScoreRowVO,StudentTotalVO,StudentCourseTotalVO,MyCourseVO}.java`
- Create: `backend/src/main/java/com/score/util/StatsCalculator.java`
- Modify: `backend/src/main/java/com/score/mapper/ScoreMapper.java`
- Create: `backend/src/main/resources/mapper/ScoreMapper.xml`
- Create: `backend/src/main/java/com/score/service/ScoreService.java`
- Create: `backend/src/main/java/com/score/controller/ScoreController.java`
- Test: `backend/src/test/java/com/score/ScoreIT.java`
- Test: `backend/src/test/java/com/score/util/StatsCalculatorTest.java`

> **协作点（学习模式）**：`StatsCalculator.rank` 是"并列同名次"的业务规则，本计划在 Step 4 给了参考实现；执行时可以让用户自己写这 5-10 行，测试已备好。

- [ ] **Step 1: 写成绩集成测试（先失败）**

`backend/src/test/java/com/score/ScoreIT.java`：

```java
package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.entity.Student;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScoreIT extends BaseIntegrationTest {

    private Exam exam(String courseName, String examName) {
        Long courseId = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getName, courseName)).getId();
        return examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, courseId)
                .eq(Exam::getName, examName));
    }

    private Student student(String studentNo) {
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo));
    }

    private String batchBody(Long studentId, Long examId, String score) {
        return """
                {"items":[{"studentId":%d,"examId":%d,"score":%s}]}"""
                .formatted(studentId, examId, score);
    }

    @Test
    void examScoresIncludeAllStudents() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        mockMvc.perform(get("/api/scores").param("examId", String.valueOf(exam.getId()))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[0].studentNo").value("2026001"));
    }

    @Test
    void batchSaveUpdatesExistingScore() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        Student student = student("2026001");
        String token = "Bearer " + adminToken();

        mockMvc.perform(post("/api/scores/batch").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(student.getId(), exam.getId(), "99")))
                .andExpect(status().isOk());

        Score saved = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, student.getId())
                .eq(Score::getExamId, exam.getId()));
        assertThat(saved.getScore()).isEqualByComparingTo("99");

        mockMvc.perform(post("/api/scores/batch").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(student.getId(), exam.getId(), "88")))
                .andExpect(status().isOk());

        Score updated = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, student.getId())
                .eq(Score::getExamId, exam.getId()));
        assertThat(updated.getScore()).isEqualByComparingTo("88");
    }

    @Test
    void scoreAboveFullScoreRejected() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        Student student = student("2026001");
        mockMvc.perform(post("/api/scores/batch")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(student.getId(), exam.getId(), "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("分数不能超过满分 100"));
    }

    @Test
    void negativeScoreRejected() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        Student student = student("2026001");
        mockMvc.perform(post("/api/scores/batch")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(student.getId(), exam.getId(), "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotReadExamScores() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        mockMvc.perform(get("/api/scores").param("examId", String.valueOf(exam.getId()))
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotReadOtherTeachersExamScores() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        mockMvc.perform(get("/api/scores").param("examId", String.valueOf(exam.getId()))
                        .header("Authorization", "Bearer " + teacher2Token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentMyCoursesReturnsTotalsWithRank() throws Exception {
        mockMvc.perform(get("/api/scores/my").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].rank").isNumber())
                .andExpect(jsonPath("$.data[0].studentCount").value(8));
    }

    @Test
    void courseTotalIsNormalizedByFullScore() throws Exception {
        Course math = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getName, "高等数学"));
        Exam extra = new Exam();
        extra.setCourseId(math.getId());
        extra.setName("补考");
        extra.setFullScore(50);
        examMapper.insert(extra);

        Student student = student("2026001");
        mockMvc.perform(post("/api/scores/batch")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(student.getId(), extra.getId(), "45")))
                .andExpect(status().isOk());

        // 三次 100 分制成绩 55/72/89 + 一次 50 分制 45（折算 90），平均 76.5
        mockMvc.perform(get("/api/scores/my").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].totalScore").value(76.5));
    }
}
```

`backend/src/test/java/com/score/util/StatsCalculatorTest.java`：

```java
package com.score.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsCalculatorTest {

    @Test
    void highestTotalRanksFirst() {
        List<BigDecimal> totals = List.of(bd("90"), bd("72"), bd("55"));
        assertThat(StatsCalculator.rank(bd("90"), totals)).isEqualTo(1);
        assertThat(StatsCalculator.rank(bd("72"), totals)).isEqualTo(2);
        assertThat(StatsCalculator.rank(bd("55"), totals)).isEqualTo(3);
    }

    @Test
    void tiedTotalsShareRankAndSkipFollowing() {
        List<BigDecimal> totals = List.of(bd("90"), bd("90"), bd("80"));
        assertThat(StatsCalculator.rank(bd("90"), totals)).isEqualTo(1);
        assertThat(StatsCalculator.rank(bd("80"), totals)).isEqualTo(3);
    }

    @Test
    void nullTotalRanksZero() {
        assertThat(StatsCalculator.rank(null, List.of(bd("90")))).isZero();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=ScoreIT,StatsCalculatorTest
```

Expected: 编译失败（`StatsCalculator`、`ScoreService` 等不存在）。

- [ ] **Step 3: 写 DTO / VO**

`dto/ScoreItem.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScoreItem {
    @NotNull(message = "学生不能为空")
    private Long studentId;

    @NotNull(message = "考试不能为空")
    private Long examId;

    @NotNull(message = "分数不能为空")
    @DecimalMin(value = "0", message = "分数不能为负数")
    private BigDecimal score;
}
```

`dto/ScoreBatchRequest.java`：

```java
package com.score.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ScoreBatchRequest {
    @NotEmpty(message = "没有需要保存的成绩")
    @Valid
    private List<ScoreItem> items;
}
```

`vo/ExamScoreRowVO.java`：

```java
package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExamScoreRowVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String className;
    private Long scoreId;
    private BigDecimal score;
}
```

`vo/StudentTotalVO.java`：

```java
package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StudentTotalVO {
    private Long studentId;
    private BigDecimal total;
}
```

`vo/StudentCourseTotalVO.java`：

```java
package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StudentCourseTotalVO {
    private Long courseId;
    private String courseName;
    private BigDecimal total;
}
```

`vo/MyCourseVO.java`：

```java
package com.score.vo;

import java.math.BigDecimal;

public record MyCourseVO(Long courseId, String courseName, BigDecimal totalScore,
                         long rank, long studentCount) {
}
```

- [ ] **Step 4: 写 StatsCalculator**

`util/StatsCalculator.java`：

```java
package com.score.util;

import java.math.BigDecimal;
import java.util.List;

public final class StatsCalculator {

    private StatsCalculator() {
    }

    /**
     * 同分同名次：排名 = 严格大于自己的总分个数 + 1。
     */
    public static long rank(BigDecimal myTotal, List<BigDecimal> allTotals) {
        if (myTotal == null) {
            return 0;
        }
        return allTotals.stream()
                .filter(total -> total != null && total.compareTo(myTotal) > 0)
                .count() + 1;
    }
}
```

- [ ] **Step 5: 扩展 ScoreMapper 与 XML**

`mapper/ScoreMapper.java`：

```java
package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.Score;
import com.score.vo.ExamScoreRowVO;
import com.score.vo.StudentCourseTotalVO;
import com.score.vo.StudentTotalVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ScoreMapper extends BaseMapper<Score> {

    List<ExamScoreRowVO> selectExamScoreRows(@Param("examId") Long examId,
                                             @Param("className") String className);

    List<StudentTotalVO> selectCourseTotals(@Param("courseId") Long courseId);

    List<StudentCourseTotalVO> selectStudentCourseTotals(@Param("studentId") Long studentId);
}
```

`backend/src/main/resources/mapper/ScoreMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.score.mapper.ScoreMapper">

    <select id="selectExamScoreRows" resultType="com.score.vo.ExamScoreRowVO">
        SELECT s.id AS studentId,
               s.student_no AS studentNo,
               s.name AS studentName,
               s.class_name AS className,
               sc.id AS scoreId,
               sc.score AS score
        FROM student s
        LEFT JOIN score sc ON sc.student_id = s.id AND sc.exam_id = #{examId}
        <where>
            <if test="className != null and className != ''">
                AND s.class_name = #{className}
            </if>
        </where>
        ORDER BY s.student_no
    </select>

    <select id="selectCourseTotals" resultType="com.score.vo.StudentTotalVO">
        SELECT sc.student_id AS studentId,
               ROUND(AVG(sc.score * 100.0 / e.full_score), 1) AS total
        FROM score sc
        JOIN exam e ON sc.exam_id = e.id
        WHERE e.course_id = #{courseId}
        GROUP BY sc.student_id
    </select>

    <select id="selectStudentCourseTotals" resultType="com.score.vo.StudentCourseTotalVO">
        SELECT c.id AS courseId,
               c.name AS courseName,
               ROUND(AVG(sc.score * 100.0 / e.full_score), 1) AS total
        FROM score sc
        JOIN exam e ON sc.exam_id = e.id
        JOIN course c ON e.course_id = c.id
        WHERE sc.student_id = #{studentId}
        GROUP BY c.id, c.name
        ORDER BY c.id
    </select>
</mapper>
```

- [ ] **Step 6: 写 ScoreService**

`service/ScoreService.java`：

```java
package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.dto.ScoreBatchRequest;
import com.score.dto.ScoreItem;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.entity.Student;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import com.score.mapper.StudentMapper;
import com.score.security.SecurityUtils;
import com.score.util.StatsCalculator;
import com.score.vo.ExamScoreRowVO;
import com.score.vo.MyCourseVO;
import com.score.vo.StudentCourseTotalVO;
import com.score.vo.StudentTotalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreMapper scoreMapper;
    private final ExamMapper examMapper;
    private final StudentMapper studentMapper;
    private final CourseService courseService;

    public List<ExamScoreRowVO> listByExam(Long examId, String className) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw BusinessException.notFound("考试不存在");
        }
        courseService.requireAccessibleCourse(exam.getCourseId());
        return scoreMapper.selectExamScoreRows(examId, className);
    }

    @Transactional
    public int saveBatch(ScoreBatchRequest request) {
        List<ScoreItem> items = request.getItems();
        if (items.isEmpty()) {
            throw new BusinessException("没有需要保存的成绩");
        }
        Map<Long, Exam> exams = new HashMap<>();
        for (ScoreItem item : items) {
            Exam exam = exams.get(item.getExamId());
            if (exam == null) {
                exam = examMapper.selectById(item.getExamId());
                if (exam == null) {
                    throw BusinessException.notFound("考试不存在");
                }
                exams.put(exam.getId(), exam);
            }
            courseService.requireAccessibleCourse(exam.getCourseId());

            if (item.getScore().compareTo(BigDecimal.valueOf(exam.getFullScore())) > 0) {
                throw new BusinessException("分数不能超过满分 " + exam.getFullScore());
            }
            if (studentMapper.selectById(item.getStudentId()) == null) {
                throw BusinessException.notFound("学生不存在");
            }

            Score existing = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                    .eq(Score::getStudentId, item.getStudentId())
                    .eq(Score::getExamId, item.getExamId()));
            if (existing != null) {
                Score update = new Score();
                update.setId(existing.getId());
                update.setScore(item.getScore());
                update.setUpdatedAt(LocalDateTime.now());
                scoreMapper.updateById(update);
            } else {
                Score score = new Score();
                score.setStudentId(item.getStudentId());
                score.setExamId(item.getExamId());
                score.setScore(item.getScore());
                score.setUpdatedAt(LocalDateTime.now());
                scoreMapper.insert(score);
            }
        }
        return items.size();
    }

    public void delete(Long id) {
        Score score = scoreMapper.selectById(id);
        if (score == null) {
            throw BusinessException.notFound("成绩不存在");
        }
        Exam exam = examMapper.selectById(score.getExamId());
        if (exam != null) {
            courseService.requireAccessibleCourse(exam.getCourseId());
        }
        scoreMapper.deleteById(id);
    }

    public List<MyCourseVO> myCourses() {
        Student student = currentStudent();
        List<StudentCourseTotalVO> totals = scoreMapper.selectStudentCourseTotals(student.getId());
        List<MyCourseVO> result = new ArrayList<>();
        for (StudentCourseTotalVO item : totals) {
            List<BigDecimal> allTotals = scoreMapper.selectCourseTotals(item.getCourseId())
                    .stream().map(StudentTotalVO::getTotal).toList();
            long rank = StatsCalculator.rank(item.getTotal(), allTotals);
            result.add(new MyCourseVO(item.getCourseId(), item.getCourseName(), item.getTotal(),
                    rank, allTotals.size()));
        }
        return result;
    }

    private Student currentStudent() {
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, SecurityUtils.currentUserId()));
        if (student == null) {
            throw BusinessException.notFound("当前账号未绑定学生档案");
        }
        return student;
    }
}
```

- [ ] **Step 7: 写 ScoreController**

`controller/ScoreController.java`：

```java
package com.score.controller;

import com.score.common.Result;
import com.score.dto.ScoreBatchRequest;
import com.score.service.ScoreService;
import com.score.vo.ExamScoreRowVO;
import com.score.vo.MyCourseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<ExamScoreRowVO>> listByExam(
            @RequestParam Long examId,
            @RequestParam(required = false) String className) {
        return Result.ok(scoreService.listByExam(examId, className));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Integer> saveBatch(@Valid @RequestBody ScoreBatchRequest request) {
        return Result.ok(scoreService.saveBatch(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Void> delete(@PathVariable Long id) {
        scoreService.delete(id);
        return Result.ok();
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<MyCourseVO>> myCourses() {
        return Result.ok(scoreService.myCourses());
    }
}
```

- [ ] **Step 8: 运行测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=ScoreIT,StatsCalculatorTest
```

Expected: 8 个成绩测试 + 3 个排名测试全部 PASS。注意 `ScoreIT.studentMyCoursesReturnsTotalsWithRank` 依赖 `GET /api/scores/my` 与种子成绩。

- [ ] **Step 9: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add score entry, upsert and student score overview"
```

---

### Task 9: 成绩统计分析

**Files:**
- Create: `backend/src/main/java/com/score/vo/{DistributionItemVO,FailingStudentVO,OverallStatsVO,CourseComparisonVO,TrendPointVO,OverviewVO}.java`
- Modify: `backend/src/main/java/com/score/mapper/ScoreMapper.java`
- Modify: `backend/src/main/resources/mapper/ScoreMapper.xml`
- Create: `backend/src/main/java/com/score/service/AnalysisService.java`
- Create: `backend/src/main/java/com/score/controller/AnalysisController.java`
- Test: `backend/src/test/java/com/score/AnalysisIT.java`

- [ ] **Step 1: 写分析集成测试（先失败）**

`backend/src/test/java/com/score/AnalysisIT.java`：

```java
package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Student;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisIT extends BaseIntegrationTest {

    private Long courseId(String name) {
        return courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getName, name)).getId();
    }

    private Exam exam(String courseName, String examName) {
        return examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, courseId(courseName))
                .eq(Exam::getName, examName));
    }

    private Long studentId(String studentNo) {
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo)).getId();
    }

    @Test
    void overviewForAdmin() throws Exception {
        mockMvc.perform(get("/api/analysis/overview")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(8))
                .andExpect(jsonPath("$.data.courseCount").value(3));
    }

    @Test
    void overviewForTeacherCountsOwnCourses() throws Exception {
        mockMvc.perform(get("/api/analysis/overview")
                        .header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(8))
                .andExpect(jsonPath("$.data.courseCount").value(2));
    }

    @Test
    void distributionFillsAllFiveBuckets() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        String body = mockMvc.perform(get("/api/analysis/distribution")
                        .param("examId", String.valueOf(exam.getId()))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].name").value("90-100"))
                .andExpect(jsonPath("$.data[4].name").value("<60"))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        int sum = 0;
        for (JsonNode item : data) {
            sum += item.path("count").asInt();
        }
        assertThat(sum).isEqualTo(8);
    }

    @Test
    void teacherCannotViewOtherCourseDistribution() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        mockMvc.perform(get("/api/analysis/distribution")
                        .param("examId", String.valueOf(exam.getId()))
                        .header("Authorization", "Bearer " + teacher2Token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void failingListRespectsThreshold() throws Exception {
        Exam exam = exam("高等数学", "期中考试");
        mockMvc.perform(get("/api/analysis/failing")
                        .param("examId", String.valueOf(exam.getId()))
                        .param("threshold", "100")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));

        mockMvc.perform(get("/api/analysis/failing")
                        .param("examId", String.valueOf(exam.getId()))
                        .param("threshold", "0")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void trendReturnsAllExamsInOrder() throws Exception {
        mockMvc.perform(get("/api/analysis/trend")
                        .param("studentId", String.valueOf(studentId("2026001")))
                        .param("courseId", String.valueOf(courseId("高等数学")))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].examName").value("第一次月考"))
                .andExpect(jsonPath("$.data[0].percent").value(55.0))
                .andExpect(jsonPath("$.data[2].percent").value(89.0));
    }

    @Test
    void studentTrendAlwaysUsesOwnData() throws Exception {
        mockMvc.perform(get("/api/analysis/trend")
                        .param("studentId", String.valueOf(studentId("2026002")))
                        .param("courseId", String.valueOf(courseId("高等数学")))
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].percent").value(55.0));
    }

    @Test
    void courseComparisonScopedForTeacher() throws Exception {
        mockMvc.perform(get("/api/analysis/course-comparison")
                        .header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=AnalysisIT
```

Expected: 编译失败（分析相关类不存在）。

- [ ] **Step 3: 写分析 VO**

`vo/DistributionItemVO.java`：

```java
package com.score.vo;

import lombok.Data;

@Data
public class DistributionItemVO {
    private String name;
    private Integer count;
}
```

`vo/FailingStudentVO.java`：

```java
package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FailingStudentVO {
    private String studentNo;
    private String name;
    private BigDecimal score;
    private Integer fullScore;
}
```

`vo/OverallStatsVO.java`：

```java
package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OverallStatsVO {
    private Long totalScores;
    private BigDecimal avgScore;
    private BigDecimal passRate;
}
```

`vo/CourseComparisonVO.java`：

```java
package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseComparisonVO {
    private Long courseId;
    private String courseName;
    private BigDecimal avgScore;
    private BigDecimal passRate;
}
```

`vo/TrendPointVO.java`：

```java
package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TrendPointVO {
    private String examName;
    private LocalDate examDate;
    private BigDecimal percent;
}
```

`vo/OverviewVO.java`：

```java
package com.score.vo;

import java.math.BigDecimal;

public record OverviewVO(long studentCount, long courseCount, BigDecimal avgScore, BigDecimal passRate) {
}
```

- [ ] **Step 4: 扩展 ScoreMapper 接口**

`mapper/ScoreMapper.java`（完整文件）：

```java
package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.Score;
import com.score.vo.CourseComparisonVO;
import com.score.vo.DistributionItemVO;
import com.score.vo.ExamScoreRowVO;
import com.score.vo.FailingStudentVO;
import com.score.vo.OverallStatsVO;
import com.score.vo.StudentCourseTotalVO;
import com.score.vo.StudentTotalVO;
import com.score.vo.TrendPointVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ScoreMapper extends BaseMapper<Score> {

    List<ExamScoreRowVO> selectExamScoreRows(@Param("examId") Long examId,
                                             @Param("className") String className);

    List<StudentTotalVO> selectCourseTotals(@Param("courseId") Long courseId);

    List<StudentCourseTotalVO> selectStudentCourseTotals(@Param("studentId") Long studentId);

    List<DistributionItemVO> selectDistribution(@Param("examId") Long examId);

    List<FailingStudentVO> selectFailing(@Param("examId") Long examId,
                                         @Param("threshold") BigDecimal threshold);

    OverallStatsVO selectOverallStats(@Param("teacherId") Long teacherId);

    List<CourseComparisonVO> selectCourseComparison(@Param("teacherId") Long teacherId);

    List<TrendPointVO> selectTrend(@Param("studentId") Long studentId,
                                   @Param("courseId") Long courseId);
}
```

- [ ] **Step 5: 在 XML 中追加统计 SQL**

在 `ScoreMapper.xml` 的 `</mapper>` 之前追加（注意：SQL 里的 `<` 必须写成 `&lt;`）：

```xml
    <select id="selectDistribution" resultType="com.score.vo.DistributionItemVO">
        SELECT bucket AS name, COUNT(*) AS count
        FROM (
            SELECT CASE
                       WHEN sc.score * 100.0 / e.full_score >= 90 THEN '90-100'
                       WHEN sc.score * 100.0 / e.full_score >= 80 THEN '80-89'
                       WHEN sc.score * 100.0 / e.full_score >= 70 THEN '70-79'
                       WHEN sc.score * 100.0 / e.full_score >= 60 THEN '60-69'
                       ELSE '&lt;60'
                   END AS bucket
            FROM score sc
            JOIN exam e ON sc.exam_id = e.id
            WHERE sc.exam_id = #{examId}
        ) t
        GROUP BY bucket
    </select>

    <select id="selectFailing" resultType="com.score.vo.FailingStudentVO">
        SELECT s.student_no AS studentNo,
               s.name AS name,
               sc.score AS score,
               e.full_score AS fullScore
        FROM score sc
        JOIN student s ON sc.student_id = s.id
        JOIN exam e ON sc.exam_id = e.id
        WHERE sc.exam_id = #{examId}
          AND sc.score * 100.0 / e.full_score &lt; #{threshold}
        ORDER BY sc.score ASC
    </select>

    <select id="selectOverallStats" resultType="com.score.vo.OverallStatsVO">
        SELECT COUNT(*) AS totalScores,
               ROUND(AVG(sc.score * 100.0 / e.full_score), 1) AS avgScore,
               ROUND(SUM(CASE WHEN sc.score * 100.0 / e.full_score >= 60 THEN 1 ELSE 0 END)
                     * 100.0 / COUNT(*), 1) AS passRate
        FROM score sc
        JOIN exam e ON sc.exam_id = e.id
        JOIN course c ON e.course_id = c.id
        <where>
            <if test="teacherId != null">
                AND c.teacher_id = #{teacherId}
            </if>
        </where>
    </select>

    <select id="selectCourseComparison" resultType="com.score.vo.CourseComparisonVO">
        SELECT c.id AS courseId,
               c.name AS courseName,
               ROUND(AVG(sc.score * 100.0 / e.full_score), 1) AS avgScore,
               ROUND(SUM(CASE WHEN sc.score * 100.0 / e.full_score >= 60 THEN 1 ELSE 0 END)
                     * 100.0 / COUNT(*), 1) AS passRate
        FROM course c
        JOIN exam e ON e.course_id = c.id
        JOIN score sc ON sc.exam_id = e.id
        <where>
            <if test="teacherId != null">
                AND c.teacher_id = #{teacherId}
            </if>
        </where>
        GROUP BY c.id, c.name
        ORDER BY c.id
    </select>

    <select id="selectTrend" resultType="com.score.vo.TrendPointVO">
        SELECT e.name AS examName,
               e.exam_date AS examDate,
               ROUND(sc.score * 100.0 / e.full_score, 1) AS percent
        FROM score sc
        JOIN exam e ON sc.exam_id = e.id
        WHERE sc.student_id = #{studentId}
          AND e.course_id = #{courseId}
        ORDER BY e.exam_date, e.id
    </select>
```

- [ ] **Step 6: 写 AnalysisService**

`service/AnalysisService.java`：

```java
package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.common.Role;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Student;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import com.score.mapper.StudentMapper;
import com.score.security.SecurityUtils;
import com.score.vo.CourseComparisonVO;
import com.score.vo.DistributionItemVO;
import com.score.vo.FailingStudentVO;
import com.score.vo.OverallStatsVO;
import com.score.vo.OverviewVO;
import com.score.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String[] RANGES = {"90-100", "80-89", "70-79", "60-69", "<60"};

    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final CourseService courseService;

    public OverviewVO overview() {
        Long teacherId = scopeTeacherId();
        long studentCount = studentMapper.selectCount(null);
        long courseCount = teacherId == null
                ? courseMapper.selectCount(null)
                : courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId));

        OverallStatsVO stats = scoreMapper.selectOverallStats(teacherId);
        BigDecimal avgScore = stats == null || stats.getAvgScore() == null
                ? BigDecimal.ZERO : stats.getAvgScore();
        BigDecimal passRate = stats == null || stats.getPassRate() == null
                ? BigDecimal.ZERO : stats.getPassRate();
        return new OverviewVO(studentCount, courseCount, avgScore, passRate);
    }

    public List<DistributionItemVO> distribution(Long examId) {
        Exam exam = requireAccessibleExam(examId);
        Map<String, Integer> counts = scoreMapper.selectDistribution(exam.getId()).stream()
                .collect(Collectors.toMap(DistributionItemVO::getName, DistributionItemVO::getCount));
        return Arrays.stream(RANGES)
                .map(range -> {
                    DistributionItemVO item = new DistributionItemVO();
                    item.setName(range);
                    item.setCount(counts.getOrDefault(range, 0));
                    return item;
                })
                .toList();
    }

    public List<FailingStudentVO> failing(Long examId, BigDecimal threshold) {
        requireAccessibleExam(examId);
        return scoreMapper.selectFailing(examId, threshold);
    }

    public List<CourseComparisonVO> courseComparison() {
        return scoreMapper.selectCourseComparison(scopeTeacherId());
    }

    public List<TrendPointVO> trend(Long studentId, Long courseId) {
        if (courseId == null) {
            throw new BusinessException("请选择课程");
        }
        Long resolvedStudentId;
        if (Role.STUDENT.equals(SecurityUtils.current().role())) {
            resolvedStudentId = currentStudent().getId();
        } else {
            if (studentId == null) {
                throw new BusinessException("请选择学生");
            }
            courseService.requireAccessibleCourse(courseId);
            resolvedStudentId = studentId;
        }
        return scoreMapper.selectTrend(resolvedStudentId, courseId);
    }

    private Exam requireAccessibleExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw BusinessException.notFound("考试不存在");
        }
        courseService.requireAccessibleCourse(exam.getCourseId());
        return exam;
    }

    private Long scopeTeacherId() {
        return Role.TEACHER.equals(SecurityUtils.current().role())
                ? SecurityUtils.currentUserId() : null;
    }

    private Student currentStudent() {
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, SecurityUtils.currentUserId()));
        if (student == null) {
            throw BusinessException.notFound("当前账号未绑定学生档案");
        }
        return student;
    }
}
```

- [ ] **Step 7: 写 AnalysisController**

`controller/AnalysisController.java`：

```java
package com.score.controller;

import com.score.common.Result;
import com.score.service.AnalysisService;
import com.score.vo.CourseComparisonVO;
import com.score.vo.DistributionItemVO;
import com.score.vo.FailingStudentVO;
import com.score.vo.OverviewVO;
import com.score.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<OverviewVO> overview() {
        return Result.ok(analysisService.overview());
    }

    @GetMapping("/distribution")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<DistributionItemVO>> distribution(@RequestParam Long examId) {
        return Result.ok(analysisService.distribution(examId));
    }

    @GetMapping("/failing")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<FailingStudentVO>> failing(
            @RequestParam Long examId,
            @RequestParam(defaultValue = "60") BigDecimal threshold) {
        return Result.ok(analysisService.failing(examId, threshold));
    }

    @GetMapping("/course-comparison")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<CourseComparisonVO>> courseComparison() {
        return Result.ok(analysisService.courseComparison());
    }

    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(
            @RequestParam(required = false) Long studentId,
            @RequestParam Long courseId) {
        return Result.ok(analysisService.trend(studentId, courseId));
    }
}
```

- [ ] **Step 8: 运行测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=AnalysisIT
```

Expected: 8 个测试全部 PASS。若分布测试报 `&lt;` 被当成字符串，检查 XML 转义；若及格率/平均分为 null，检查 service 的空值兜底。

- [ ] **Step 9: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add score analysis endpoints"
```

---

### Task 10: 用户（教师）账号管理

**Files:**
- Create: `backend/src/main/java/com/score/dto/UserCreateRequest.java`
- Create: `backend/src/main/java/com/score/dto/StatusRequest.java`
- Create: `backend/src/main/java/com/score/service/UserService.java`
- Create: `backend/src/main/java/com/score/controller/UserController.java`
- Test: `backend/src/test/java/com/score/UserIT.java`

- [ ] **Step 1: 写用户管理集成测试（先失败）**

`backend/src/test/java/com/score/UserIT.java`：

```java
package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.User;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIT extends BaseIntegrationTest {

    private User user(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Test
    void adminListsTeachers() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "TEACHER")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void teacherCannotAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTeacherAndResetPassword() throws Exception {
        String body = """
                {"username":"teacher9","realName":"王老师","password":"abcdef"}""";
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TEACHER"));

        User created = user("teacher9");

        mockMvc.perform(put("/api/users/" + created.getId() + "/password")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        // 重置后可用初始密码登录
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                        post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"teacher9\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();
    }

    @Test
    void duplicateUsernameRejected() throws Exception {
        String body = """
                {"username":"teacher1","realName":"重复老师"}""";
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void disableUserBlocksLogin() throws Exception {
        String body = """
                {"username":"teacher8","realName":"停用老师"}""";
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        User created = user("teacher8");

        mockMvc.perform(put("/api/users/" + created.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher8\",\"password\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=UserIT
```

Expected: 编译失败（`UserService` 不存在）。

- [ ] **Step 3: 写 DTO**

`dto/UserCreateRequest.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;
}
```

`dto/StatusRequest.java`：

```java
package com.score.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusRequest {
    @NotNull(message = "请指定启用状态")
    private Boolean enabled;
}
```

- [ ] **Step 4: 写 UserService**

`service/UserService.java`：

```java
package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.score.common.BusinessException;
import com.score.common.PageResult;
import com.score.common.Role;
import com.score.dto.UserCreateRequest;
import com.score.entity.User;
import com.score.mapper.UserMapper;
import com.score.security.SecurityUtils;
import com.score.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(String role, String keyword, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(role), User::getRole, role)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getRealName, keyword))
                .orderByAsc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserVO> list = result.getRecords().stream().map(UserVO::from).toList();
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public UserVO create(UserCreateRequest request) {
        long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists > 0) {
            throw BusinessException.conflict("用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        String rawPassword = StringUtils.hasText(request.getPassword())
                ? request.getPassword() : "123456";
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.TEACHER);
        user.setEnabled(true);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    public void setStatus(Long id, boolean enabled) {
        requireUser(id);
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BusinessException("不能修改当前登录账号的状态");
        }
        User update = new User();
        update.setId(id);
        update.setEnabled(enabled);
        userMapper.updateById(update);
    }

    public void resetPassword(Long id) {
        requireUser(id);
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode("123456"));
        userMapper.updateById(update);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }
}
```

- [ ] **Step 5: 写 UserController**

`controller/UserController.java`：

```java
package com.score.controller;

import com.score.common.PageResult;
import com.score.common.Result;
import com.score.dto.StatusRequest;
import com.score.dto.UserCreateRequest;
import com.score.service.UserService;
import com.score.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<UserVO>> page(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(userService.page(role, keyword, page, size));
    }

    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        userService.setStatus(id, request.getEnabled());
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.ok();
    }
}
```

- [ ] **Step 6: 运行测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test -Dtest=UserIT
```

Expected: 5 个测试全部 PASS。注意 `UserController` 类级别的 `@PreAuthorize` 依赖 `@EnableMethodSecurity`（Task 4 已配置）。

- [ ] **Step 7: 运行全部后端测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test
```

Expected: 全部 PASS（AuthIT / StudentIT / CourseIT / ExamIT / ScoreIT / AnalysisIT / UserIT / StatsCalculatorTest / MapperWiringTest / ApplicationContextTest）。

- [ ] **Step 8: 提交**

```bash
cd /Users/mac/workspace/score && git add backend && git commit -m "feat: add teacher account management"
```

---

### Task 11: 前端脚手架、API 封装与登录页

**Files:**
- Create: `frontend/`（Vite 脚手架）
- Modify: `frontend/vite.config.js`
- Create: `frontend/src/api/request.js`
- Create: `frontend/src/api/index.js`
- Create: `frontend/src/stores/auth.js`
- Create: `frontend/src/views/Login.vue`
- Modify: `frontend/src/main.js`
- Modify: `frontend/src/App.vue`
- Create: `frontend/src/router/index.js`

- [ ] **Step 1: 生成脚手架并安装依赖**

```bash
cd /Users/mac/workspace/score
npm create vite@latest frontend -- --template vue
cd frontend
npm install
npm install element-plus @element-plus/icons-vue axios pinia vue-router echarts
npm install -D vitest
rm -f src/components/HelloWorld.vue src/style.css
```

Expected: `node_modules` 安装完成，无报错。

- [ ] **Step 2: 配置 Vite（端口与代理）**

`frontend/vite.config.js`：

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 3: 写 main.js 与 App.vue**

`frontend/src/main.js`：

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
```

`frontend/src/App.vue`：

```vue
<template>
  <router-view />
</template>
```

- [ ] **Step 4: 写 Axios 封装**

`frontend/src/api/request.js`：

```js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const body = response.data
    if (body.code !== 0) {
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message))
    }
    return body.data
  },
  error => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (window.location.pathname !== '/login') {
        ElMessage.error(message || '登录已过期，请重新登录')
        window.location.href = '/login'
      }
    } else {
      ElMessage.error(message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
```

- [ ] **Step 5: 写 API 集合**

`frontend/src/api/index.js`：

```js
import request from './request'

export const authApi = {
  login: data => request.post('/auth/login', data),
  me: () => request.get('/auth/me'),
  changePassword: data => request.put('/auth/password', data)
}

export const studentApi = {
  page: params => request.get('/students', { params }),
  detail: id => request.get(`/students/${id}`),
  create: data => request.post('/students', data),
  update: (id, data) => request.put(`/students/${id}`, data),
  remove: id => request.delete(`/students/${id}`),
  openAccount: id => request.post(`/students/${id}/account`)
}

export const courseApi = {
  list: params => request.get('/courses', { params }),
  create: data => request.post('/courses', data),
  update: (id, data) => request.put(`/courses/${id}`, data),
  remove: id => request.delete(`/courses/${id}`)
}

export const examApi = {
  list: courseId => request.get('/exams', { params: { courseId } }),
  create: data => request.post('/exams', data),
  update: (id, data) => request.put(`/exams/${id}`, data),
  remove: id => request.delete(`/exams/${id}`)
}

export const scoreApi = {
  byExam: (examId, className) => request.get('/scores', { params: { examId, className } }),
  saveBatch: items => request.post('/scores/batch', { items }),
  remove: id => request.delete(`/scores/${id}`),
  myCourses: () => request.get('/scores/my')
}

export const analysisApi = {
  overview: () => request.get('/analysis/overview'),
  distribution: examId => request.get('/analysis/distribution', { params: { examId } }),
  failing: (examId, threshold) => request.get('/analysis/failing', { params: { examId, threshold } }),
  courseComparison: () => request.get('/analysis/course-comparison'),
  trend: (studentId, courseId) => request.get('/analysis/trend', { params: { studentId, courseId } })
}

export const userApi = {
  page: params => request.get('/users', { params }),
  create: data => request.post('/users', data),
  setStatus: (id, enabled) => request.put(`/users/${id}/status`, { enabled }),
  resetPassword: id => request.put(`/users/${id}/password`)
}
```

- [ ] **Step 6: 写 Pinia 认证 store**

`frontend/src/stores/auth.js`：

```js
import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    role: state => state.user?.role || '',
    isLoggedIn: state => Boolean(state.token)
  },
  actions: {
    async login(form) {
      const data = await authApi.login(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
```

- [ ] **Step 7: 写登录页**

`frontend/src/views/Login.vue`：

```vue
<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">学生成绩管理系统</h2>
      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码"
                    :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <p class="hint">演示账号：admin / teacher1 / 2026001，密码均为 123456</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login(form)
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a93, #4a69bd);
}
.login-card {
  width: 380px;
  padding: 8px 12px;
}
.title {
  text-align: center;
  margin: 8px 0 24px;
  color: #303133;
}
.submit {
  width: 100%;
}
.hint {
  margin-top: 16px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
```

- [ ] **Step 8: 写最小路由**

`frontend/src/router/index.js`（后续任务会逐步添加子路由）：

```js
import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    { path: '/:pathMatch(.*)*', redirect: '/login' }
  ]
})

router.beforeEach(to => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  return true
})

export default router
```

- [ ] **Step 9: 构建验证**

```bash
cd /Users/mac/workspace/score/frontend && npm run build
```

Expected: 构建成功，产出 `dist/`。

- [ ] **Step 10: 提交**

```bash
cd /Users/mac/workspace/score && git add frontend && git commit -m "feat: scaffold vue frontend with element plus and login page"
```

---

### Task 12: 主布局、路由守卫与仪表盘

**Files:**
- Create: `frontend/src/utils/permission.js`
- Create: `frontend/src/utils/permission.test.js`
- Create: `frontend/src/components/BaseChart.vue`
- Create: `frontend/src/layouts/MainLayout.vue`
- Create: `frontend/src/views/Dashboard.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/package.json`（加 test 脚本）

- [ ] **Step 1: 写权限判断的单元测试（先失败）**

`frontend/src/utils/permission.test.js`：

```js
import { describe, expect, it } from 'vitest'
import { canAccess } from './permission'

describe('canAccess', () => {
  it('allows any role when no roles are required', () => {
    expect(canAccess('STUDENT', undefined)).toBe(true)
    expect(canAccess('STUDENT', [])).toBe(true)
  })

  it('allows a role included in the requirement', () => {
    expect(canAccess('ADMIN', ['ADMIN', 'TEACHER'])).toBe(true)
    expect(canAccess('TEACHER', ['ADMIN', 'TEACHER'])).toBe(true)
  })

  it('denies a role not included in the requirement', () => {
    expect(canAccess('STUDENT', ['ADMIN'])).toBe(false)
  })
})
```

在 `frontend/package.json` 的 `scripts` 中加入：

```json
"test": "vitest run"
```

- [ ] **Step 2: 运行测试（预期失败）**

```bash
cd /Users/mac/workspace/score/frontend && npm test
```

Expected: 失败，提示无法解析 `./permission`。

- [ ] **Step 3: 实现权限工具**

`frontend/src/utils/permission.js`：

```js
export function canAccess(role, requiredRoles) {
  if (!requiredRoles || requiredRoles.length === 0) {
    return true
  }
  return requiredRoles.includes(role)
}
```

- [ ] **Step 4: 运行测试（预期通过）**

```bash
cd /Users/mac/workspace/score/frontend && npm test
```

Expected: 3 个测试 PASS。

- [ ] **Step 5: 写 BaseChart 组件**

`frontend/src/components/BaseChart.vue`：

```vue
<template>
  <div ref="el" :style="{ width: '100%', height: height }"></div>
</template>

<script setup>
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' }
})

const el = ref(null)
let chart = null
let observer = null

onMounted(() => {
  chart = echarts.init(el.value)
  chart.setOption(props.option)
  observer = new ResizeObserver(() => chart && chart.resize())
  observer.observe(el.value)
})

watch(
  () => props.option,
  option => chart && chart.setOption(option, true),
  { deep: true }
)

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  if (chart) chart.dispose()
})
</script>
```

- [ ] **Step 6: 写主布局**

`frontend/src/layouts/MainLayout.vue`：

```vue
<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="brand">成绩管理系统</div>
      <el-menu :default-active="route.path" router background-color="#1f2d3d"
               text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="welcome">{{ auth.user?.realName || auth.user?.username }}</span>
        <el-tag size="small">{{ roleLabel }}</el-tag>
        <el-button link type="primary" @click="logout">退出登录</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const allMenus = [
  { path: '/dashboard', title: '仪表盘', icon: 'Odometer', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
  { path: '/students', title: '学生管理', icon: 'User', roles: ['ADMIN', 'TEACHER'] },
  { path: '/courses', title: '课程管理', icon: 'Reading', roles: ['ADMIN'] },
  { path: '/users', title: '用户管理', icon: 'Avatar', roles: ['ADMIN'] },
  { path: '/exams', title: '考试管理', icon: 'Calendar', roles: ['ADMIN', 'TEACHER'] },
  { path: '/scores', title: '成绩录入', icon: 'EditPen', roles: ['ADMIN', 'TEACHER'] },
  { path: '/analysis', title: '成绩分析', icon: 'DataAnalysis', roles: ['ADMIN', 'TEACHER'] },
  { path: '/my-scores', title: '我的成绩', icon: 'Tickets', roles: ['STUDENT'] }
]

const menus = computed(() => allMenus.filter(item => item.roles.includes(auth.role)))
const roleLabel = computed(
  () => ({ ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }[auth.role] || '')
)

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.brand {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-weight: 600;
  background: #17222f;
}
.el-aside {
  background: #1f2d3d;
}
.el-menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.welcome {
  color: #303133;
}
</style>
```

- [ ] **Step 7: 写仪表盘页面**

`frontend/src/views/Dashboard.vue`：

```vue
<template>
  <div v-if="auth.role === 'STUDENT'">
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>已修课程</span><strong>{{ myCourses.length }}</strong></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>平均总评</span><strong>{{ averageScore }}</strong></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block">
      <template #header>我的课程总评</template>
      <el-table :data="myCourses" stripe>
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="totalScore" label="总评" width="120" />
        <el-table-column label="排名" width="160">
          <template #default="{ row }">{{ row.rank }} / {{ row.studentCount }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>

  <div v-else>
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :span="6">
        <el-card>
          <div class="stat"><span>{{ card.label }}</span><strong>{{ card.value }}</strong></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>分数段分布</span>
          <div class="selects">
            <el-select v-model="courseId" placeholder="课程" style="width: 180px" @change="loadExams">
              <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-select v-model="examId" placeholder="考试" style="width: 180px" @change="loadDistribution">
              <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </div>
        </div>
      </template>
      <BaseChart :option="distributionOption" height="320px" />
    </el-card>

    <el-card class="block">
      <template #header>课程平均分 / 及格率对比</template>
      <BaseChart :option="comparisonOption" height="340px" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { analysisApi, courseApi, examApi, scoreApi } from '../api'
import { useAuthStore } from '../stores/auth'
import BaseChart from '../components/BaseChart.vue'

const auth = useAuthStore()
const overview = ref({ studentCount: 0, courseCount: 0, avgScore: 0, passRate: 0 })
const comparison = ref([])
const courses = ref([])
const exams = ref([])
const courseId = ref(null)
const examId = ref(null)
const distribution = ref([])
const myCourses = ref([])

const cards = computed(() => [
  { label: '学生总数', value: overview.value.studentCount },
  { label: '课程总数', value: overview.value.courseCount },
  { label: '全校平均分', value: overview.value.avgScore },
  { label: '及格率', value: `${overview.value.passRate}%` }
])

const averageScore = computed(() => {
  if (!myCourses.value.length) return '0.0'
  const sum = myCourses.value.reduce((acc, course) => acc + Number(course.totalScore || 0), 0)
  return (sum / myCourses.value.length).toFixed(1)
})

const distributionOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie',
    radius: ['40%', '65%'],
    data: distribution.value.map(item => ({ name: item.name, value: item.count }))
  }]
}))

const comparisonOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['平均分', '及格率'] },
  xAxis: { type: 'category', data: comparison.value.map(item => item.courseName) },
  yAxis: [
    { type: 'value', name: '平均分', max: 100 },
    { type: 'value', name: '及格率(%)', max: 100 }
  ],
  series: [
    { name: '平均分', type: 'bar', data: comparison.value.map(item => item.avgScore) },
    { name: '及格率', type: 'line', yAxisIndex: 1, data: comparison.value.map(item => item.passRate) }
  ]
}))

async function loadExams() {
  exams.value = await examApi.list(courseId.value)
  examId.value = exams.value[0]?.id ?? null
  await loadDistribution()
}

async function loadDistribution() {
  distribution.value = examId.value ? await analysisApi.distribution(examId.value) : []
}

onMounted(async () => {
  if (auth.role === 'STUDENT') {
    myCourses.value = await scoreApi.myCourses()
    return
  }
  overview.value = await analysisApi.overview()
  comparison.value = await analysisApi.courseComparison()
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await loadExams()
  }
})
</script>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stat span {
  color: #909399;
  font-size: 14px;
}
.stat strong {
  font-size: 28px;
  color: #303133;
}
.block {
  margin-top: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.selects {
  display: flex;
  gap: 8px;
}
</style>
```

- [ ] **Step 8: 更新路由（加入布局、守卫与仪表盘）**

`frontend/src/router/index.js`：

```js
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { canAccess } from '../utils/permission'
import Login from '../views/Login.vue'
import MainLayout from '../layouts/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: Dashboard }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(to => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (to.path === '/login') {
    return token ? '/dashboard' : true
  }
  if (!token || !user) {
    return '/login'
  }
  if (!canAccess(user.role, to.meta.roles)) {
    ElMessage.warning('无权访问该页面')
    return '/dashboard'
  }
  return true
})

export default router
```

- [ ] **Step 9: 构建与测试**

```bash
cd /Users/mac/workspace/score/frontend && npm test && npm run build
```

Expected: Vitest 3 个测试 PASS，构建成功。

- [ ] **Step 10: 提交**

```bash
cd /Users/mac/workspace/score && git add frontend && git commit -m "feat: add main layout, route guard and dashboard"
```

---

### Task 13: 学生管理页面

**Files:**
- Create: `frontend/src/views/Students.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 写学生管理页**

`frontend/src/views/Students.vue`：

```vue
<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="学号 / 姓名" clearable style="width: 200px"
                @keyup.enter="search" />
      <el-input v-model="query.className" placeholder="班级" clearable style="width: 200px"
                @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button v-if="canEdit" type="success" @click="openCreate">新增学生</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="gender" label="性别" width="80" />
      <el-table-column prop="className" label="班级" min-width="140" />
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="账号" width="100">
        <template #default="{ row }">
          <el-tag :type="row.hasAccount ? 'success' : 'info'">
            {{ row.hasAccount ? '已开通' : '未开通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" :disabled="row.hasAccount" @click="openAccount(row)">
            开通账号
          </el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total"
                   :page-size="query.size" :current-page="query.page"
                   @current-change="onPageChange" />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑学生' : '新增学生'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="学号" prop="studentNo"><el-input v-model="form.studentNo" /></el-form-item>
      <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="性别">
        <el-radio-group v-model="form.gender">
          <el-radio value="男">男</el-radio>
          <el-radio value="女">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="班级"><el-input v-model="form.className" /></el-form-item>
      <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentApi } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canEdit = computed(() => auth.role === 'ADMIN')

const query = reactive({ keyword: '', className: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null, studentNo: '', name: '', gender: '男', className: '', phone: '', email: ''
})
const rules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await studentApi.page(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onPageChange(page) {
  query.page = page
  load()
}

function resetForm() {
  Object.assign(form, {
    id: null, studentNo: '', name: '', gender: '男', className: '', phone: '', email: ''
  })
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id, studentNo: row.studentNo, name: row.name, gender: row.gender,
    className: row.className, phone: row.phone, email: row.email
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await studentApi.update(form.id, form)
  } else {
    await studentApi.create(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function openAccount(row) {
  await ElMessageBox.confirm(
    `确定为「${row.name}」开通登录账号吗？用户名与初始密码均为学号。`, '提示', { type: 'warning' }
  )
  await studentApi.openAccount(row.id)
  ElMessage.success('账号已开通，初始密码 123456')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(
    `删除学生「${row.name}」将同时删除其成绩与账号，确定继续吗？`, '警告', { type: 'warning' }
  )
  await studentApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 2: 注册路由**

在 `frontend/src/router/index.js` 的 `children` 数组中、`dashboard` 之后加入：

```js
{ path: 'students', component: () => import('../views/Students.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
```

- [ ] **Step 3: 构建验证**

```bash
cd /Users/mac/workspace/score/frontend && npm run build
```

Expected: 构建成功。

- [ ] **Step 4: 提交**

```bash
cd /Users/mac/workspace/score && git add frontend && git commit -m "feat: add student management page"
```

---

### Task 14: 课程管理与用户管理页面

**Files:**
- Create: `frontend/src/views/Courses.vue`
- Create: `frontend/src/views/Users.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 写课程管理页**

`frontend/src/views/Courses.vue`：

```vue
<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="课程名称" clearable style="width: 220px"
                @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openCreate">新增课程</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="courseCode" label="课程编号" width="120" />
      <el-table-column prop="name" label="课程名称" min-width="160" />
      <el-table-column prop="credit" label="学分" width="80" />
      <el-table-column prop="semester" label="学期" width="140" />
      <el-table-column prop="teacherName" label="任课教师" width="120" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑课程' : '新增课程'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="课程编号" prop="courseCode"><el-input v-model="form.courseCode" /></el-form-item>
      <el-form-item label="课程名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="学分">
        <el-input-number v-model="form.credit" :min="0" :max="20" :step="0.5" />
      </el-form-item>
      <el-form-item label="学期">
        <el-input v-model="form.semester" placeholder="如 2026-2027-1" />
      </el-form-item>
      <el-form-item label="任课教师">
        <el-select v-model="form.teacherId" placeholder="请选择教师" clearable style="width: 100%">
          <el-option v-for="t in teachers" :key="t.id" :label="t.realName" :value="t.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi, userApi } from '../api'

const list = ref([])
const teachers = ref([])
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null, courseCode: '', name: '', credit: 3, semester: '', teacherId: null
})
const rules = {
  courseCode: [{ required: true, message: '请输入课程编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    list.value = await courseApi.list({ keyword: keyword.value })
  } finally {
    loading.value = false
  }
}

async function loadTeachers() {
  const data = await userApi.page({ role: 'TEACHER', size: 100 })
  teachers.value = data.list
}

function resetForm() {
  Object.assign(form, {
    id: null, courseCode: '', name: '', credit: 3, semester: '', teacherId: null
  })
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id, courseCode: row.courseCode, name: row.name,
    credit: row.credit, semester: row.semester, teacherId: row.teacherId
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await courseApi.update(form.id, form)
  } else {
    await courseApi.create(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？`, '提示', { type: 'warning' })
  await courseApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadTeachers()])
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
```

- [ ] **Step 2: 写用户管理页**

`frontend/src/views/Users.vue`：

```vue
<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 140px" @change="search">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="教师" value="TEACHER" />
        <el-option label="学生" value="STUDENT" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="用户名 / 姓名" clearable style="width: 220px"
                @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button type="success" @click="openCreate">新增教师</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="realName" label="姓名" width="150" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">{{ roleLabel(row.role) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total"
                   :page-size="query.size" :current-page="query.page"
                   @current-change="onPageChange" />
  </el-card>

  <el-dialog v-model="dialogVisible" title="新增教师" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
      <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" /></el-form-item>
      <el-form-item label="初始密码">
        <el-input v-model="form.password" placeholder="留空则使用 123456" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../api'

const query = reactive({ role: '', keyword: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ username: '', realName: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

const roleLabel = role => ({ ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }[role] || role)

async function load() {
  loading.value = true
  try {
    const data = await userApi.page(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onPageChange(page) {
  query.page = page
  load()
}

function openCreate() {
  Object.assign(form, { username: '', realName: '', password: '' })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  await userApi.create(form)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  await load()
}

async function toggle(row) {
  await userApi.setStatus(row.id, !row.enabled)
  ElMessage.success('状态已更新')
  await load()
}

async function resetPassword(row) {
  await ElMessageBox.confirm(`确定将「${row.realName}」的密码重置为 123456 吗？`, '提示',
    { type: 'warning' })
  await userApi.resetPassword(row.id)
  ElMessage.success('密码已重置')
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 3: 注册路由**

在 `frontend/src/router/index.js` 的 `children` 中追加：

```js
{ path: 'courses', component: () => import('../views/Courses.vue'), meta: { roles: ['ADMIN'] } },
{ path: 'users', component: () => import('../views/Users.vue'), meta: { roles: ['ADMIN'] } },
```

- [ ] **Step 4: 构建验证**

```bash
cd /Users/mac/workspace/score/frontend && npm run build
```

Expected: 构建成功。

- [ ] **Step 5: 提交**

```bash
cd /Users/mac/workspace/score && git add frontend && git commit -m "feat: add course and user management pages"
```

---

### Task 15: 考试管理与成绩录入页面

**Files:**
- Create: `frontend/src/views/Exams.vue`
- Create: `frontend/src/views/Scores.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 写考试管理页**

`frontend/src/views/Exams.vue`：

```vue
<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="courseId" placeholder="选择课程" style="width: 220px" @change="load">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button type="success" :disabled="!courseId" @click="openCreate">新增考试</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="name" label="考试名称" min-width="160" />
      <el-table-column prop="examDate" label="考试日期" width="140" />
      <el-table-column prop="fullScore" label="满分" width="100" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑考试' : '新增考试'" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="考试名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="考试日期">
        <el-date-picker v-model="form.examDate" type="date" value-format="YYYY-MM-DD"
                        style="width: 100%" />
      </el-form-item>
      <el-form-item label="满分" prop="fullScore">
        <el-input-number v-model="form.fullScore" :min="1" :max="200" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi, examApi } from '../api'

const courses = ref([])
const courseId = ref(null)
const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, courseId: null, name: '', examDate: '', fullScore: 100 })
const rules = {
  name: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  fullScore: [{ required: true, message: '请输入满分', trigger: 'change' }]
}

async function loadCourses() {
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await load()
  }
}

async function load() {
  if (!courseId.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = await examApi.list(courseId.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, courseId: courseId.value, name: '', examDate: '', fullScore: 100
  })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, courseId: courseId.value, name: row.name,
    examDate: row.examDate, fullScore: row.fullScore
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await examApi.update(form.id, form)
  } else {
    await examApi.create(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除考试「${row.name}」将同时删除其成绩，确定继续吗？`, '警告',
    { type: 'warning' })
  await examApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(loadCourses)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
```

- [ ] **Step 2: 写成绩录入页**

`frontend/src/views/Scores.vue`：

```vue
<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="courseId" placeholder="选择课程" style="width: 200px" @change="loadExams">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="examId" placeholder="选择考试" style="width: 200px" @change="loadRows">
        <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
      </el-select>
      <el-input v-model="className" placeholder="按班级筛选（可选）" clearable style="width: 200px"
                @keyup.enter="loadRows" />
      <el-button type="primary" @click="loadRows">刷新</el-button>
      <el-button type="success" :loading="saving" :disabled="!examId" @click="save">保存成绩</el-button>
    </div>

    <el-table :data="rows" stripe v-loading="loading">
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="studentName" label="姓名" width="120" />
      <el-table-column prop="className" label="班级" min-width="150" />
      <el-table-column label="分数" width="200">
        <template #default="{ row }">
          <el-input-number v-model="row.score" :min="0" :max="fullScore" :step="0.5"
                           controls-position="right" style="width: 140px" />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { courseApi, examApi, scoreApi } from '../api'

const courses = ref([])
const exams = ref([])
const courseId = ref(null)
const examId = ref(null)
const rows = ref([])
const className = ref('')
const loading = ref(false)
const saving = ref(false)

const fullScore = computed(
  () => exams.value.find(exam => exam.id === examId.value)?.fullScore ?? 100
)

async function loadCourses() {
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await loadExams()
  }
}

async function loadExams() {
  exams.value = await examApi.list(courseId.value)
  examId.value = exams.value[0]?.id ?? null
  await loadRows()
}

async function loadRows() {
  if (!examId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await scoreApi.byExam(examId.value, className.value || undefined)
  } finally {
    loading.value = false
  }
}

async function save() {
  const items = rows.value
    .filter(row => row.score !== null && row.score !== undefined && row.score !== '')
    .map(row => ({ studentId: row.studentId, examId: examId.value, score: row.score }))
  if (!items.length) {
    ElMessage.warning('没有可保存的成绩')
    return
  }
  saving.value = true
  try {
    await scoreApi.saveBatch(items)
    ElMessage.success(`已保存 ${items.length} 条成绩`)
    await loadRows()
  } finally {
    saving.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
```

- [ ] **Step 3: 注册路由**

在 `frontend/src/router/index.js` 的 `children` 中追加：

```js
{ path: 'exams', component: () => import('../views/Exams.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
{ path: 'scores', component: () => import('../views/Scores.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
```

- [ ] **Step 4: 构建验证**

```bash
cd /Users/mac/workspace/score/frontend && npm run build
```

Expected: 构建成功。

- [ ] **Step 5: 提交**

```bash
cd /Users/mac/workspace/score && git add frontend && git commit -m "feat: add exam management and score entry pages"
```

---

### Task 16: 成绩分析页面

**Files:**
- Create: `frontend/src/views/Analysis.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 写成绩分析页**

`frontend/src/views/Analysis.vue`：

```vue
<template>
  <el-card>
    <el-tabs v-model="tab">
      <el-tab-pane label="分数段分布" name="distribution">
        <div class="toolbar">
          <el-select v-model="courseId" placeholder="课程" style="width: 200px" @change="loadExams">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="examId" placeholder="考试" style="width: 200px" @change="loadDistribution">
            <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
        </div>
        <BaseChart :option="distributionOption" height="360px" />
      </el-tab-pane>

      <el-tab-pane label="课程对比" name="comparison">
        <BaseChart :option="comparisonOption" height="360px" />
        <el-table :data="comparison" stripe class="table">
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="avgScore" label="平均分" width="140" />
          <el-table-column prop="passRate" label="及格率(%)" width="140" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="不及格预警" name="failing">
        <div class="toolbar">
          <el-select v-model="courseId" placeholder="课程" style="width: 200px" @change="loadExams">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="examId" placeholder="考试" style="width: 200px">
            <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
          <el-input-number v-model="threshold" :min="0" :max="100" :step="5" />
          <el-button type="primary" @click="loadFailing">查询</el-button>
        </div>
        <el-table :data="failing" stripe>
          <el-table-column prop="studentNo" label="学号" width="140" />
          <el-table-column prop="name" label="姓名" width="140" />
          <el-table-column prop="score" label="原始分" width="120" />
          <el-table-column prop="fullScore" label="满分" width="100" />
          <el-table-column label="折算分" width="120">
            <template #default="{ row }">
              {{ (Number(row.score) * 100 / Number(row.fullScore)).toFixed(1) }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { analysisApi, courseApi, examApi } from '../api'
import BaseChart from '../components/BaseChart.vue'

const tab = ref('distribution')
const courses = ref([])
const exams = ref([])
const courseId = ref(null)
const examId = ref(null)
const distribution = ref([])
const comparison = ref([])
const failing = ref([])
const threshold = ref(60)

const distributionOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: distribution.value.map(item => item.name) },
  yAxis: { type: 'value', name: '人数', minInterval: 1 },
  series: [{
    type: 'bar',
    barWidth: '50%',
    data: distribution.value.map(item => item.count)
  }]
}))

const comparisonOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['平均分', '及格率'] },
  xAxis: { type: 'category', data: comparison.value.map(item => item.courseName) },
  yAxis: [
    { type: 'value', name: '平均分', max: 100 },
    { type: 'value', name: '及格率(%)', max: 100 }
  ],
  series: [
    { name: '平均分', type: 'bar', data: comparison.value.map(item => item.avgScore) },
    { name: '及格率', type: 'line', yAxisIndex: 1, data: comparison.value.map(item => item.passRate) }
  ]
}))

async function loadExams() {
  exams.value = await examApi.list(courseId.value)
  examId.value = exams.value[0]?.id ?? null
  await loadDistribution()
}

async function loadDistribution() {
  distribution.value = examId.value ? await analysisApi.distribution(examId.value) : []
}

async function loadFailing() {
  if (!examId.value) return
  failing.value = await analysisApi.failing(examId.value, threshold.value)
}

watch(tab, async value => {
  if (value === 'failing') {
    await loadFailing()
  } else if (value === 'distribution') {
    await loadDistribution()
  }
})

onMounted(async () => {
  comparison.value = await analysisApi.courseComparison()
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await loadExams()
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.table {
  margin-top: 16px;
}
</style>
```

- [ ] **Step 2: 注册路由**

在 `frontend/src/router/index.js` 的 `children` 中追加：

```js
{ path: 'analysis', component: () => import('../views/Analysis.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
```

- [ ] **Step 3: 构建验证**

```bash
cd /Users/mac/workspace/score/frontend && npm run build
```

Expected: 构建成功。

- [ ] **Step 4: 提交**

```bash
cd /Users/mac/workspace/score && git add frontend && git commit -m "feat: add score analysis page"
```

---

### Task 17: 学生端「我的成绩」页面

**Files:**
- Create: `frontend/src/views/MyScores.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 写我的成绩页**

`frontend/src/views/MyScores.vue`：

```vue
<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>已修课程</span><strong>{{ totals.length }}</strong></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>平均总评</span><strong>{{ average }}</strong></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block">
      <template #header>课程总评与排名</template>
      <el-table :data="totals" stripe>
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="totalScore" label="总评" width="120" />
        <el-table-column label="排名" width="160">
          <template #default="{ row }">{{ row.rank }} / {{ row.studentCount }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>历次考试趋势</span>
          <el-select v-model="courseId" style="width: 200px" @change="loadTrend">
            <el-option v-for="c in totals" :key="c.courseId" :label="c.courseName"
                       :value="c.courseId" />
          </el-select>
        </div>
      </template>
      <BaseChart :option="trendOption" height="340px" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { analysisApi, scoreApi } from '../api'
import BaseChart from '../components/BaseChart.vue'

const totals = ref([])
const courseId = ref(null)
const trend = ref([])

const average = computed(() => {
  if (!totals.value.length) return '0.0'
  const sum = totals.value.reduce((acc, item) => acc + Number(item.totalScore || 0), 0)
  return (sum / totals.value.length).toFixed(1)
})

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: trend.value.map(item => item.examName) },
  yAxis: { type: 'value', name: '折算分', min: 0, max: 100 },
  series: [{
    type: 'line',
    smooth: true,
    areaStyle: {},
    data: trend.value.map(item => item.percent)
  }]
}))

async function loadTrend() {
  trend.value = courseId.value ? await analysisApi.trend(undefined, courseId.value) : []
}

onMounted(async () => {
  totals.value = await scoreApi.myCourses()
  if (totals.value.length) {
    courseId.value = totals.value[0].courseId
    await loadTrend()
  }
})
</script>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stat span {
  color: #909399;
  font-size: 14px;
}
.stat strong {
  font-size: 28px;
  color: #303133;
}
.block {
  margin-top: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
```

- [ ] **Step 2: 注册路由**

在 `frontend/src/router/index.js` 的 `children` 中追加：

```js
{ path: 'my-scores', component: () => import('../views/MyScores.vue'), meta: { roles: ['STUDENT'] } },
```

- [ ] **Step 3: 构建验证**

```bash
cd /Users/mac/workspace/score/frontend && npm run build
```

Expected: 构建成功。

- [ ] **Step 4: 提交**

```bash
cd /Users/mac/workspace/score && git add frontend && git commit -m "feat: add student my-scores page"
```

---

### Task 18: 端到端验证与 README

**Files:**
- Create: `README.md`

- [ ] **Step 1: 运行后端全部测试**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q test
```

Expected: 全部 PASS，`target/surefire-reports` 中无失败。

- [ ] **Step 2: 前端测试与构建**

```bash
cd /Users/mac/workspace/score/frontend && npm test && npm run build
```

Expected: Vitest PASS，构建成功。

- [ ] **Step 3: 启动后端**

```bash
cd /Users/mac/workspace/score/backend && ./mvnw -q spring-boot:run
```

Expected: 控制台出现 `Tomcat started on port 8080`；首次启动打印"初始化演示数据"并创建 11 个用户、8 名学生、3 门课程、9 场考试。保持运行。

- [ ] **Step 4: 启动前端**

新终端：

```bash
cd /Users/mac/workspace/score/frontend && npm run dev
```

Expected: 输出 `http://localhost:5173`。

- [ ] **Step 5: 浏览器验证关键流程**

用浏览器（或 Chrome DevTools MCP）访问 `http://localhost:5173`，依次验证：

1. `admin / 123456` 登录 → 仪表盘显示 4 张卡片（学生 8、课程 3）、分布饼图、课程对比图。
2. 学生管理 → 搜索"张"→ 分页 → 新增学生 `2026999 / 测试生` → 开通账号 → 用 `2026999 / 123456` 登录能看到"我的成绩"。
3. 课程管理 → 新增课程 `CS301 / 软件工程`，分配给张老师。
4. 考试管理 → 选择"高等数学"→ 新增考试"补考"（满分 50）→ 在成绩录入页给某学生录入 45 → 保存 → 刷新后分数仍在。
5. 成绩分析 → 分数段分布图 5 个柱；不及格预警阈值调到 100 → 出现名单。
6. 用 `teacher2 / 123456` 登录 → 只能看到"大学英语"；访问 `/analysis` 时课程下拉只有自己的课程。
7. 用 `2026001 / 123456` 登录 → 只能看到"我的成绩"菜单，直接访问 `/students` 被提示无权访问。
8. 退出登录 → 回到登录页；未登录直接访问 `/dashboard` → 被重定向到 `/login`。

Expected: 全部符合预期；若某步失败，先看后端日志与浏览器 Network 面板中的响应。

- [ ] **Step 6: 写 README**

`README.md`：

```markdown
# 学生成绩管理系统

基于 Spring Boot 3 + Vue 3 的前后端分离学生成绩管理系统，支持管理员 / 教师 / 学生三种角色，
包含学生信息管理、课程与考试管理、成绩录入与多维度统计分析。

## 技术栈

- 后端：Java 17、Spring Boot 3.5、Spring Security + JWT、MyBatis-Plus、MySQL 8
- 前端：Vue 3、Vite、Element Plus、Pinia、Vue Router、ECharts
- 测试：JUnit 5 + MockMvc（H2 内存库）、Vitest

## 功能

- 登录认证：JWT 无状态认证，三角色权限控制，账号启用/禁用
- 学生管理：分页查询、增删改、按学号开通学生登录账号
- 课程管理：分配任课教师，教师只能管理自己课程
- 考试管理：按课程维护多次考试（名称、日期、满分）
- 成绩管理：按考试批量录入/修改成绩，自动校验分数范围
- 成绩分析：
  - 总览仪表盘（学生数、课程数、平均分、及格率）
  - 分数段分布（90-100 / 80-89 / 70-79 / 60-69 / <60）
  - 个人历次考试趋势
  - 课程平均分与及格率对比
  - 不及格预警（阈值可调）
- 学生端：查看本人课程总评、排名与趋势

## 快速开始

### 1. 环境要求

- JDK 17+（本项目在 Java 20 下运行通过，编译目标 17）
- Node.js 18+
- MySQL 8

### 2. 初始化数据库

```bash
brew install mysql
brew services start mysql
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS score_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

如果 MySQL root 有密码，修改 `backend/src/main/resources/application.yml` 中的 `username` / `password`。
建表脚本由 `schema.sql` 在启动时自动执行（`CREATE TABLE IF NOT EXISTS`）。

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

后端运行在 <http://localhost:8080>。首次启动会自动写入演示数据。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。

## 演示账号

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | admin | 123456 |
| 教师 | teacher1（张老师，高等数学/数据结构） | 123456 |
| 教师 | teacher2（李老师，大学英语） | 123456 |
| 学生 | 2026001 ~ 2026008 | 123456 |

## 测试

```bash
cd backend && ./mvnw test          # 集成测试使用 H2，不依赖 MySQL
cd frontend && npm test            # Vitest 单元测试
cd frontend && npm run build       # 构建校验
```

## 目录结构

```
backend/    Spring Boot 服务（controller / service / mapper / security）
frontend/   Vue 3 前端（views / api / stores / router）
docs/       设计文档与实现计划
```

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/login | 登录获取 JWT |
| GET | /api/students | 学生分页查询 |
| POST | /api/students/{id}/account | 开通学生账号 |
| GET/POST/PUT/DELETE | /api/courses | 课程管理 |
| GET/POST/PUT/DELETE | /api/exams | 考试管理 |
| GET | /api/scores?examId= | 考试名单与成绩 |
| POST | /api/scores/batch | 批量录入成绩 |
| GET | /api/scores/my | 学生查看本人成绩 |
| GET | /api/analysis/overview | 总览统计 |
| GET | /api/analysis/distribution | 分数段分布 |
| GET | /api/analysis/trend | 个人成绩趋势 |
| GET | /api/analysis/course-comparison | 课程对比 |
| GET | /api/analysis/failing | 不及格预警 |
```

- [ ] **Step 7: 停止服务并提交**

```bash
# 停止开发服务器（Ctrl+C 两次），然后：
cd /Users/mac/workspace/score
git add README.md
git commit -m "docs: add project readme with setup and demo accounts"
git log --oneline
```

Expected: 提交历史清晰，最后一条为 README 提交。

---

## 验证清单（完成前逐项确认）

- [ ] `cd backend && ./mvnw test` 全绿
- [ ] `cd frontend && npm test && npm run build` 全绿
- [ ] 管理员登录后仪表盘有数据、两张图表正常渲染
- [ ] 教师 teacher1 只能看到自己 2 门课程；访问他人课程接口返回 403
- [ ] 学生 2026001 只能看自己的成绩，直接调 `/api/students` 返回 403
- [ ] 成绩录入支持重复保存（覆盖），超出满分返回 400
- [ ] 分数段分布始终返回 5 个区间，人数合计等于参考人数
- [ ] 删除考试/学生时其成绩一并删除
- [ ] 未登录访问任意页面跳转登录页
- [ ] README 中的启动步骤可被他人照做成功

## 风险与备注

- 首次 `./mvnw` 会下载 Maven 与依赖，耗时取决于网络；可配置阿里云 Maven 镜像加速。
- 若 start.spring.io 只提供 Spring Boot 4.x，可手动将 `pom.xml` 的 parent 改为 3.5.x 并核对依赖坐标。
- MySQL root 空密码仅用于本地开发；如需共享环境请创建独立用户并收紧 CORS 白名单。
- JWT 无状态注销（token 到期前仍有效）为课程项目取舍，生产环境需引入黑名单或刷新令牌。

