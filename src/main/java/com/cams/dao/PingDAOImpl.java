package com.cams.dao;

import com.cams.model.PingResult;
import com.cams.util.DBConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Implementation of PingDAO that executes parameterized queries against Oracle Database.
 */
public class PingDAOImpl implements PingDAO {

    private static final String PING_SQL =
            "SELECT ? AS ECHO_MSG, 'CONNECTED' AS STATUS, " +
            "TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24:MI:SS.FF3') AS SERVER_TIME FROM DUAL";

    @Override
    public PingResult executePing(String echoMessage) throws SQLException {
        long startTime = System.currentTimeMillis();

        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String dbProduct = metaData.getDatabaseProductName();
            String dbVersion = metaData.getDatabaseProductVersion();

            try (PreparedStatement stmt = conn.prepareStatement(PING_SQL)) {
                // Strictly use parameterized placeholders
                stmt.setString(1, echoMessage != null ? echoMessage : "CAMS JDBC Verification");

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String echo = rs.getString("ECHO_MSG");
                        String status = rs.getString("STATUS");
                        String serverTime = rs.getString("SERVER_TIME");
                        long elapsed = System.currentTimeMillis() - startTime;

                        // Return a clean Model object, never raw ResultSet
                        return new PingResult(status, dbProduct, dbVersion, serverTime, echo, elapsed);
                    } else {
                        throw new SQLException("No result returned from Oracle DUAL query.");
                    }
                }
            }
        }
    }
}
