package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Monitors a database over JDBC.
 *
 * @author  AO Industries, Inc.
 */
abstract public class JdbcPortMonitor extends PortMonitor {

    private final Map<String,String> monitoringParameters;

    public JdbcPortMonitor(String ipAddress, int port, Map<String,String> monitoringParameters) {
        super(ipAddress, port);
        this.monitoringParameters = monitoringParameters;
    }

    @Override
    final public String checkPort() throws Exception {
        // Get the configuration
        String username = monitoringParameters.get("username");
        if(username==null || username.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the username parameter");
        String password = monitoringParameters.get("password");
        if(password==null || password.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the password parameter");
        String database = monitoringParameters.get("database");
        if(database==null || database.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the database parameter");
        String query = monitoringParameters.get("query");
        if(query==null || query.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the query parameter");

        Class.forName(getDriver()).newInstance();
        Connection conn = DriverManager.getConnection(
            getJdbcUrl(ipAddress, port, database),
            username,
            password
        );
        try {
            Statement stmt = conn.createStatement();
            try {
                ResultSet results = stmt.executeQuery(query);
                try {
                    if(!results.next()) throw new SQLException("No row returned");
                    ResultSetMetaData metaData = results.getMetaData();
                    int colCount = metaData.getColumnCount();
                    if(colCount==0) throw new SQLException("No columns returned");
                    if(colCount>1) throw new SQLException("More than one column returned");
                    String result = results.getString(1);
                    if(results.next()) throw new SQLException("More than one row returned");
                    return result;
                } finally {
                    results.close();
                }
            } finally {
                stmt.close();
            }
        } finally {
            conn.close();
        }
    }

    /**
     * Gets the driver classname.
     */
    protected abstract String getDriver();

    /**
     * Generates the JDBC URL.
     */
    protected abstract String getJdbcUrl(String ipAddress, int port, String database);
}
