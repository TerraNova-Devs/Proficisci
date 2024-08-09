package de.mcterranova.proficisci.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.mcterranova.proficisci.Proficisci;

import java.sql.Connection;
import java.sql.SQLException;

public class HikariCPDatabase {
    private static HikariCPDatabase instance;
    private HikariDataSource dataSource;

    private HikariCPDatabase() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/minecraft");
        config.setUsername("root");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        dataSource = new HikariDataSource(config);
        prepareTables();
    }

    public static synchronized HikariCPDatabase getInstance() throws SQLException {
        if (instance == null) {
            instance = new HikariCPDatabase();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void prepareTables() throws SQLException {
        try (Connection connection = getConnection()) {
            connection.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS barrel_locations (" +
                            "region_name VARCHAR(255), " +
                            "world VARCHAR(255), " +
                            "x DOUBLE, " +
                            "y DOUBLE, " +
                            "z DOUBLE, " +
                            "name VARCHAR(255), " +
                            "owner VARCHAR(36), " +
                            "PRIMARY KEY (region_name, world, x, y, z))"
            );
        }
    }

    public void closeConnection() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
