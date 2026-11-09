package com.jyh03.roomflow.integration;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Creates/drops ONLY a random database owned by this test run; never the application database. */
final class IsolatedDatabase {
    private static String adminUrl, username, password, ownedName;
    private static boolean createdByThisRun;
    static synchronized void configure(DynamicPropertyRegistry registry) {
        String url;
        String driver;
        String ddl;
        username = "sa";
        password = "";
        if (Boolean.getBoolean("roomflow.test.mysql")) {
            Properties local = new Properties();
            Path path = Path.of("src/main/resources/application-local.properties");
            try {
                if (Files.exists(path)) {
                    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { local.load(reader); }
                }
                String source = setting("ROOMFLOW_TEST_MYSQL_URL", local.getProperty("spring.datasource.url"));
                username = setting("ROOMFLOW_TEST_MYSQL_USER", local.getProperty("spring.datasource.username"));
                password = setting("ROOMFLOW_TEST_MYSQL_PASSWORD", local.getProperty("spring.datasource.password", ""));
                if (source == null || username == null) throw new IllegalStateException("MySQL test credentials are required.");
                Matcher match = Pattern.compile("^jdbc:mysql://([^/]+)/[^?]*(\\?.*)?$").matcher(source);
                if (!match.matches()) throw new IllegalStateException("Use a single-host jdbc:mysql://host:port/database URL.");
                String suffix = match.group(2) == null ? "" : match.group(2);
                adminUrl = "jdbc:mysql://" + match.group(1) + "/" + suffix;
                ownedName = "roomflow_test_" + UUID.randomUUID().toString().replace("-", "");
                try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                     Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE DATABASE " + ownedName + " CHARACTER SET utf8mb4");
                    createdByThisRun = true;
                }
                url = "jdbc:mysql://" + match.group(1) + "/" + ownedName + suffix;
                try (Connection connection = DriverManager.getConnection(url, username, password)) {
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource("SQL/schema.sql"));
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource("SQL/insert_initial_data.sql"));
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource("SQL/insert_initial_data.sql"));
                    try (Statement statement = connection.createStatement();
                         ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM rooms")) {
                        result.next();
                        if (result.getInt(1) != 13) throw new IllegalStateException("Room seed must be idempotent (13 rooms).");
                    }
                }
                Runtime.getRuntime().addShutdownHook(new Thread(IsolatedDatabase::cleanup, "owned-test-db-cleanup"));
                driver = "com.mysql.cj.jdbc.Driver";
                ddl = "validate";
            } catch (Exception error) {
                cleanup();
                // Do not include connection URLs/passwords in diagnostics.
                throw new IllegalStateException("Isolated MySQL setup failed (" + error.getClass().getSimpleName()
                        + "). Check connectivity and CREATE/DROP DATABASE privileges.");
            }
        } else {
            url = "jdbc:h2:mem:workflow_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
            driver = "org.h2.Driver";
            ddl = "create-drop";
        }
        String finalUrl = url, finalDriver = driver, finalDdl = ddl;
        registry.add("spring.datasource.url", () -> finalUrl);
        registry.add("spring.datasource.driver-class-name", () -> finalDriver);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> finalDdl);
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    static synchronized void cleanup() {
        if (ownedName == null || !createdByThisRun) return;
        if (!ownedName.matches("roomflow_test_[0-9a-f]{32}")) throw new IllegalStateException("Unsafe test database name.");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE " + ownedName);
            ownedName = null;
            createdByThisRun = false;
        } catch (SQLException error) {
            // Only a generated database name is disclosed; credentials are never logged.
            System.err.println("Temporary test database cleanup required: " + ownedName);
        }
    }
}
