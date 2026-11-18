package com.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.entity.User;
import com.score.support.BaseIntegrationTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher9\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
        Assertions.assertThat(token).isNotBlank();
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
