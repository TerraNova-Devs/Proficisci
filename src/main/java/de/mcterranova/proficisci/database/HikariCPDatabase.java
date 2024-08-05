package de.mcterranova.proficisci.database;

import de.mcterranova.proficisci.Proficisci;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public class HikariCPDatabase {

    private final Proficisci plugin;
    private final String user;
    private final String password;
    public HikariDataSource dataSource;

    public HikariCPDatabase(Proficisci plugin) throws SQLException {
        this.plugin = plugin;
        this.user = "root"; // Replace with your MySQL username
        this.password = ""; // Replace with your MySQL password

        HikariConfig config = getHikariConfig();
        dataSource = new HikariDataSource(config);
        prepareTables();
    }

    private @NotNull HikariConfig getHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/minecraft"); // Replace with your MySQL database URL
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(15);
        config.setMinimumIdle(10);
        config.setMaxLifetime(1800000);
        config.setKeepaliveTime(0);
        config.setConnectionTimeout(5000);
        config.setLeakDetectionThreshold(100000);
        config.setPoolName("ProficisciHikariPool");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("useLocalTransactionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        return config;
    }

    private void prepareTables() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String schemaSQL = new String(Objects.requireNonNull(plugin.getResource("database/mysql_schema.sql")).readAllBytes(), StandardCharsets.UTF_8).trim();
            if (schemaSQL.isEmpty()) {
                throw new IllegalStateException("Schema SQL is empty. Please check the mysql_schema.sql file.");
            }
            String[] databaseSchema = schemaSQL.split(";");
            try (Statement statement = connection.createStatement()) {
                for (String tableCreationStatement : databaseSchema) {
                    if (!tableCreationStatement.trim().isEmpty()) {
                        statement.execute(tableCreationStatement);
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to create database tables. Please ensure you are running MySQL v8.0+ " +
                        "and that your connecting user account has privileges to create tables.", e);
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to establish a connection to the MySQL database. " +
                    "Please check the supplied database credentials in the config file", e);
        }
    }

    public void closeConnection() {
        dataSource.close();
    }
}
