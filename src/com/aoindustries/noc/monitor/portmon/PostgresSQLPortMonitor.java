package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2009-2011 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
import com.aoindustries.aoserv.client.PostgresDatabase;
import com.aoindustries.aoserv.client.validator.InetAddress;
import com.aoindustries.aoserv.client.validator.NetPort;
import java.util.Map;

/**
 * Monitors a PostgreSQL database.
 *
 * @author  AO Industries, Inc.
 */
public class PostgresSQLPortMonitor extends JdbcPortMonitor {

    public PostgresSQLPortMonitor(InetAddress ipAddress, NetPort port, Map<String,String> monitoringParameters) {
        super(ipAddress, port, monitoringParameters);
    }

    @Override
    protected String getDriver() {
        return PostgresDatabase.JDBC_DRIVER;
    }

    @Override
    protected String getJdbcUrl(InetAddress ipAddress, NetPort port, String database) {
        String address = ipAddress.toString();
        if(address.indexOf(':')==-1) return "jdbc:postgresql://"+address+":"+port+"/"+database;
        return "jdbc:postgresql://["+address+"]:"+port+"/"+database;
    }
}
