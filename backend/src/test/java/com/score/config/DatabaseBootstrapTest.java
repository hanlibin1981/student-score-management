package com.score.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseBootstrapTest {

    @Test
    void parsesDatabaseNameAndServerUrl() {
        DatabaseBootstrap.Target target = DatabaseBootstrap.parse(
                "jdbc:mysql://localhost:3306/score_db?useSSL=false&serverTimezone=Asia/Shanghai");

        assertThat(target.database()).isEqualTo("score_db");
        assertThat(target.serverUrl())
                .isEqualTo("jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=Asia/Shanghai");
    }

    @Test
    void parsesUrlWithoutQueryString() {
        DatabaseBootstrap.Target target = DatabaseBootstrap.parse("jdbc:mysql://db:3306/score_db");

        assertThat(target.database()).isEqualTo("score_db");
        assertThat(target.serverUrl()).isEqualTo("jdbc:mysql://db:3306/");
    }

    @Test
    void rejectsUrlWithoutDatabaseName() {
        assertThatThrownBy(() -> DatabaseBootstrap.parse("jdbc:mysql://localhost:3306/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法的数据库名");
    }

    @Test
    void rejectsUnsafeDatabaseName() {
        assertThatThrownBy(() -> DatabaseBootstrap.parse("jdbc:mysql://localhost:3306/score;drop"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法的数据库名");
    }
}
