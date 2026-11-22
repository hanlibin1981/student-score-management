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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void sessionsListDistinctExamNamesOrderedByDate() throws Exception {
        mockMvc.perform(get("/api/exams/sessions")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("第一次月考"))
                .andExpect(jsonPath("$.data[1].name").value("期中考试"))
                .andExpect(jsonPath("$.data[2].name").value("期末考试"));
    }

    @Test
    void teacherSessionsExcludeOtherTeachersCourses() throws Exception {
        Exam exam = new Exam();
        exam.setCourseId(courseId("大学英语"));
        exam.setName("英语口语加试");
        exam.setFullScore(100);
        examMapper.insert(exam);

        mockMvc.perform(get("/api/exams/sessions")
                        .header("Authorization", "Bearer " + teacher1Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '英语口语加试')]").isEmpty());

        mockMvc.perform(get("/api/exams/sessions")
                        .header("Authorization", "Bearer " + teacher2Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '英语口语加试')]").isNotEmpty());
    }

    @Test
    void studentCannotListExamSessions() throws Exception {
        mockMvc.perform(get("/api/exams/sessions")
                        .header("Authorization", "Bearer " + studentToken()))
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
