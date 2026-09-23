package com.sms.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Creates JDBC connections to the {@code student_management} database.
 *
 * <p>Configuration is read once from {@code db.properties} on the classpath, so
 * no credential ever appears in source code.
 */
public final class DatabaseConnection {

    private static final String CONFIG_FILE = "db.properties";
    private static final Properties CONFIG = new Properties();

    static {
        loadConfig();
    }

    private DatabaseConnection() {
        // Utility class: holds only static members, so it is never instantiated.
    }

    private static void loadConfig() {
        try (InputStream in = DatabaseConnection.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (in == null) {
                throw new IllegalStateException(CONFIG_FILE
                        + " was not found on the classpath. Expected it at "
                        + "src/main/resources/" + CONFIG_FILE);
            }
            CONFIG.load(in);

        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + CONFIG_FILE, e);
        }
    }

    private static String required(String key) {
        String value = CONFIG.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required property '" + key + "' in " + CONFIG_FILE);
        }
        return value;
    }

    /**
     * Opens a brand new connection to MySQL.
     *
     * <p>The caller owns the returned connection and must close it - always with
     * try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                required("db.url"),
                required("db.username"),
                required("db.password"));
    }
}
