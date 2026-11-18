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
