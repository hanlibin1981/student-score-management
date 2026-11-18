package com.score.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * 启动引导：先检查数据库是否存在，再决定是否初始化。
 *
 * <ul>
 *   <li>库不存在：自动建库，然后建表并写入演示数据；</li>
 *   <li>库存在但没有核心表：只建表并写入演示数据；</li>
 *   <li>库存在且已初始化：跳过全部初始化。</li>
 * </ul>
 *
 * 非 MySQL 数据源（如测试环境的 H2）直接跳过，由测试配置自行建表。
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class DatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);
    private static final String MYSQL_PREFIX = "jdbc:mysql://";
    private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private final Environment environment;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith(MYSQL_PREFIX)) {
            return;
        }
        Target target = parse(url);
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");

        if (!databaseExists(target, username, password)) {
            createDatabase(target, username, password);
            log.info("数据库 {} 不存在，已自动创建", target.database());
        } else if (initialized(target.database())) {
            log.info("数据库 {} 已存在且已完成初始化，跳过初始化", target.database());
            return;
        } else {
            log.info("数据库 {} 已存在但尚未建表，继续初始化", target.database());
        }

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        log.info("数据库 {} 建表完成", target.database());
    }

    static Target parse(String url) {
        if (!url.startsWith(MYSQL_PREFIX)) {
            throw new IllegalArgumentException("仅支持 MySQL URL: " + url);
        }
        String rest = url.substring(MYSQL_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("MySQL URL 缺少数据库名: " + url);
        }
        String authority = rest.substring(0, slash);
        String after = rest.substring(slash + 1);
        int question = after.indexOf('?');
        String database = question >= 0 ? after.substring(0, question) : after;
        String query = question >= 0 ? after.substring(question) : "";
        if (!DATABASE_NAME.matcher(database).matches()) {
            throw new IllegalArgumentException("非法的数据库名: " + database);
        }
        return new Target(MYSQL_PREFIX + authority + "/" + query, database);
    }

    private boolean databaseExists(Target target, String username, String password) throws SQLException {
        String sql = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?";
        try (Connection connection = DriverManager.getConnection(target.serverUrl(), username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, target.database());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void createDatabase(Target target, String username, String password) throws SQLException {
        String sql = "CREATE DATABASE IF NOT EXISTS `" + target.database()
                + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
        try (Connection connection = DriverManager.getConnection(target.serverUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean initialized(String database) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'sys_user'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    record Target(String serverUrl, String database) {
    }
}
