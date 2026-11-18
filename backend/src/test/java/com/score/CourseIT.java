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
