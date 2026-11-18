package com.score;

import com.score.entity.User;
import com.score.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MapperWiringTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertsAndReadsBackAUser() {
        User user = new User();
        user.setUsername("mapper_test");
        user.setPassword("x");
        user.setRealName("映射测试");
        user.setRole("ADMIN");
        user.setEnabled(true);

        userMapper.insert(user);
        User loaded = userMapper.selectById(user.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getUsername()).isEqualTo("mapper_test");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }
}
