package com.cams.model;

import java.io.Serializable;

/**
 * Model representing database round-trip verification / health status.
 */
public class PingResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String status;
    private String databaseProductName;
    private String databaseProductVersion;
    private String serverTimestamp;
    private String echoMessage;
    private long queryExecutionMillis;

    public PingResult() {
    }

    public PingResult(String status, String databaseProductName, String databaseProductVersion,
                      String serverTimestamp, String echoMessage, long queryExecutionMillis) {
        this.status = status;
        this.databaseProductName = databaseProductName;
        this.databaseProductVersion = databaseProductVersion;
        this.serverTimestamp = serverTimestamp;
        this.echoMessage = echoMessage;
        this.queryExecutionMillis = queryExecutionMillis;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDatabaseProductName() {
        return databaseProductName;
    }

    public void setDatabaseProductName(String databaseProductName) {
        this.databaseProductName = databaseProductName;
    }

    public String getDatabaseProductVersion() {
        return databaseProductVersion;
    }

    public void setDatabaseProductVersion(String databaseProductVersion) {
        this.databaseProductVersion = databaseProductVersion;
    }

    public String getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public String getEchoMessage() {
        return echoMessage;
    }

    public void setEchoMessage(String echoMessage) {
        this.echoMessage = echoMessage;
    }

    public long getQueryExecutionMillis() {
        return queryExecutionMillis;
    }

    public void setQueryExecutionMillis(long queryExecutionMillis) {
        this.queryExecutionMillis = queryExecutionMillis;
    }
}
