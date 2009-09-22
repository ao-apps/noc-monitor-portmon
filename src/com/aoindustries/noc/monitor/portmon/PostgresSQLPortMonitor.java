package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
import com.aoindustries.aoserv.client.PostgresDatabase;
import java.util.Map;

/**
 * Monitors a PostgreSQL database.
 *
 * @author  AO Industries, Inc.
 */
public class PostgresSQLPortMonitor extends JdbcPortMonitor {

    public PostgresSQLPortMonitor(String ipAddress, int port, Map<String,String> monitoringParameters) {
        super(ipAddress, port, monitoringParameters);
    }

    @Override
    protected String getDriver() {
        return PostgresDatabase.JDBC_DRIVER;
    }

    @Override
    protected String getJdbcUrl(String ipAddress, int port, String database) {
        return "jdbc:postgresql://"+ipAddress+":"+port+"/"+database;
    }
}
