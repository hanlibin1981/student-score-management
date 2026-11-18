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
