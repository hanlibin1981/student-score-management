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
        requireStudent(id);
        long exists = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, request.getStudentNo())
                .ne(Student::getId, id));
        if (exists > 0) {
            throw BusinessException.conflict("学号已存在");
        }
        Student update = new Student();
        update.setId(id);
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
