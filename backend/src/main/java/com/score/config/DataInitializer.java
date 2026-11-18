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
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
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
            log.info("检测到已有用户数据，跳过演示数据初始化");
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

        log.info("演示数据初始化完成：{} 个用户，{} 名学生，{} 门课程",
                userMapper.selectCount(null), students.size(), courses.size());
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

    private Course createCourse(String code, String name, BigDecimal credit,
                                String semester, Long teacherId) {
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
