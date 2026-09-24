package com.cams.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to manage Oracle Database JDBC connections using DriverManager.
 * Reads configuration externally from db.properties, with dynamic reloading support.
 */
public final class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());
    private static final Properties PROPERTIES = new Properties();
    private static boolean driverLoaded = false;

    static {
        loadProperties();
    }

    private DBConnection() {
        // Prevent instantiation
    }

    /**
     * Loads or reloads database configuration dynamically.
     * Prioritizes the source file on disk so changes take effect immediately without server restart.
     */
    public static synchronized void loadProperties() {
        File file = new File("src/main/resources/db.properties");
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                PROPERTIES.load(in);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not load db.properties directly from disk", e);
            }
        } else {
            try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (in != null) {
                    PROPERTIES.load(in);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not load db.properties from classpath", e);
            }
        }

        if (!driverLoaded) {
            try {
                String driver = PROPERTIES.getProperty("db.driver", "oracle.jdbc.OracleDriver");
                Class.forName(driver);
                driverLoaded = true;
                LOGGER.info("Oracle JDBC Driver loaded: " + driver);
            } catch (ClassNotFoundException e) {
                LOGGER.log(Level.SEVERE, "Oracle JDBC Driver class not found in classpath", e);
            }
        }
    }

    /**
     * Obtains a new JDBC Connection to the Oracle Database.
     *
     * @return active java.sql.Connection
     * @throws SQLException if a database access error occurs or password placeholder is detected
     */
    public static Connection getConnection() throws SQLException {
        loadProperties();

        String url = PROPERTIES.getProperty("db.url");
        String user = PROPERTIES.getProperty("db.username");
        String password = PROPERTIES.getProperty("db.password");

        if (password == null || password.trim().isEmpty() || "YOUR_ORACLE_PASSWORD_HERE".equals(password.trim())) {
            String errorMsg = "Database password is not configured! Please update 'db.password' in src/main/resources/db.properties with your actual Oracle password.";
            LOGGER.warning(errorMsg);
            throw new SQLException(errorMsg);
        }

        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Quick connection test helper that checks if credentials are configured.
     */
    public static boolean isConfigured() {
        loadProperties();
        String password = PROPERTIES.getProperty("db.password");
        return password != null && !password.trim().isEmpty() && !"YOUR_ORACLE_PASSWORD_HERE".equals(password.trim());
    }

    public static String getJdbcUrl() {
        loadProperties();
        return PROPERTIES.getProperty("db.url", "N/A");
    }

    public static String getDbUser() {
        loadProperties();
        return PROPERTIES.getProperty("db.username", "N/A");
    }
}
