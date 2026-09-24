package com.cams.service;

import com.cams.dao.PingDAO;
import com.cams.dao.PingDAOImpl;
import com.cams.model.PingResult;
import com.cams.util.DBConnection;

import java.sql.SQLException;

/**
 * Service layer for connection verification and health checks.
 * Encapsulates business logic, validation, and delegates data access to DAO.
 */
public class PingService {

    private final PingDAO pingDAO;

    public PingService() {
        this.pingDAO = new PingDAOImpl();
    }

    public PingService(PingDAO pingDAO) {
        this.pingDAO = pingDAO;
    }

    /**
     * Verifies the Oracle database connection round-trip.
     *
     * @param clientMessage optional message passed from client
     * @return PingResult model object
     * @throws SQLException if a database connectivity error occurs
     */
    public PingResult checkDatabaseHealth(String clientMessage) throws SQLException {
        // Business logic / input sanitization
        String sanitizedMessage = (clientMessage == null || clientMessage.trim().isEmpty())
                ? "CAMS Round-Trip Ping"
                : clientMessage.trim();

        if (sanitizedMessage.length() > 255) {
            sanitizedMessage = sanitizedMessage.substring(0, 255);
        }

        // Delegate to DAO
        return pingDAO.executePing(sanitizedMessage);
    }

    /**
     * Checks if the configuration has been updated from default placeholders.
     */
    public boolean isDbConfigured() {
        return DBConnection.isConfigured();
    }

    public String getConfiguredJdbcUrl() {
        return DBConnection.getJdbcUrl();
    }

    public String getConfiguredDbUser() {
        return DBConnection.getDbUser();
    }
}
