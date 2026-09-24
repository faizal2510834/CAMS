package com.cams.dao;

import com.cams.model.PingResult;
import java.sql.SQLException;

/**
 * Data Access Object interface for connection verification and health checks.
 */
public interface PingDAO {

    /**
     * Executes a parameterized health-check query against Oracle Database
     * using PreparedStatement and returns a PingResult model object.
     *
     * @param echoMessage a test parameter to verify PreparedStatement parameter binding
     * @return PingResult model object populated from Oracle
     * @throws SQLException on database errors
     */
    PingResult executePing(String echoMessage) throws SQLException;
}
