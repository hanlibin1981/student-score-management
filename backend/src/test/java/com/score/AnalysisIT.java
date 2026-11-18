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
