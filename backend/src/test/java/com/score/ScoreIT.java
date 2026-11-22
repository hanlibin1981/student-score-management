package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.entity.Student;
import com.score.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
    void sessionMatrixPutsAllSubjectsInOneRow() throws Exception {
        Exam math = exam("高等数学", "期中考试");
        Exam data = exam("数据结构", "期中考试");
        Exam english = exam("大学英语", "期中考试");

        mockMvc.perform(get("/api/scores/session").param("examName", "期中考试")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examName").value("期中考试"))
                .andExpect(jsonPath("$.data.exams.length()").value(3))
                .andExpect(jsonPath("$.data.exams[0].courseName").value("高等数学"))
                .andExpect(jsonPath("$.data.rows.length()").value(8))
                .andExpect(jsonPath("$.data.rows[0].studentNo").value("2026001"))
                .andExpect(jsonPath("$.data.rows[0].scores['" + math.getId() + "']").value(72))
                .andExpect(jsonPath("$.data.rows[0].scores['" + data.getId() + "']").value(85))
                .andExpect(jsonPath("$.data.rows[0].scores['" + english.getId() + "']").value(57));
    }

    @Test
    void sessionForTeacherOnlyContainsOwnCourses() throws Exception {
        mockMvc.perform(get("/api/scores/session").param("examName", "期中考试")
                        .header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exams.length()").value(2))
                .andExpect(jsonPath("$.data.exams[0].courseName").value("高等数学"))
                .andExpect(jsonPath("$.data.exams[1].courseName").value("数据结构"));

        mockMvc.perform(get("/api/scores/session").param("examName", "期中考试")
                        .header("Authorization", "Bearer " + teacher2Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exams.length()").value(1))
                .andExpect(jsonPath("$.data.exams[0].courseName").value("大学英语"));
    }

    @Test
    void sessionFiltersStudentsByClass() throws Exception {
        mockMvc.perform(get("/api/scores/session").param("examName", "期中考试")
                        .param("className", "软件工程2601")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(8))
                .andExpect(jsonPath("$.data.exams.length()").value(3));

        mockMvc.perform(get("/api/scores/session").param("examName", "期中考试")
                        .param("className", "不存在的班级")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(0))
                .andExpect(jsonPath("$.data.exams.length()").value(3));
    }

    @Test
    void unknownExamSessionReturnsEmptyMatrix() throws Exception {
        mockMvc.perform(get("/api/scores/session").param("examName", "不存在的考试")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exams.length()").value(0))
                .andExpect(jsonPath("$.data.rows.length()").value(0));
    }

    @Test
    void studentCannotReadSessionMatrix() throws Exception {
        mockMvc.perform(get("/api/scores/session").param("examName", "期中考试")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
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
