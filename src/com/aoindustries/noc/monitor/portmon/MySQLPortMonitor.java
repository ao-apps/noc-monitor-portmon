package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
import java.util.Map;

/**
 * Monitors a MySQL database.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLPortMonitor extends JdbcPortMonitor {

    public MySQLPortMonitor(String ipAddress, int port, Map<String,String> monitoringParameters) {
        super(ipAddress, port, monitoringParameters);
    }

    @Override
    protected String getDriver() {
        return "com.mysql.jdbc.Driver";
    }

    @Override
    protected String getJdbcUrl(String ipAddress, int port, String database) {
        return "jdbc:mysql://"+ipAddress+":"+port+"/"+database;
    }
}
