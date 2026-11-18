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
