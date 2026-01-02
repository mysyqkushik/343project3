package com.groupxx.greengrocer.db;

import com.groupxx.greengrocer.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class DbAdapter {
    private static DbAdapter instance;
    private final HikariDataSource dataSource;

    private DbAdapter() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.DB_URL);
        cfg.setUsername(AppConfig.DB_USER);
        cfg.setPassword(AppConfig.DB_PASSWORD);

        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(5000);
        cfg.setValidationTimeout(3000);

        this.dataSource = new HikariDataSource(cfg);
    }

    public static synchronized DbAdapter getInstance() {
        if (instance == null)
            instance = new DbAdapter();
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void testConnection() throws SQLException {
        try (Connection c = getConnection()) {
            if (c == null || c.isClosed())
                throw new SQLException("Connection is null/closed.");
        }
    }
}
