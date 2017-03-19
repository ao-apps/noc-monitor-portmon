/*
 * Copyright 2009-2013, 2016, 2017 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.aoserv.client.PostgresDatabase;
import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;

/**
 * Monitors a PostgreSQL database.
 *
 * @author  AO Industries, Inc.
 */
public class PostgresSQLPortMonitor extends JdbcPortMonitor {

	public PostgresSQLPortMonitor(InetAddress ipAddress, Port port, HttpParameters monitoringParameters) {
		super(ipAddress, port, monitoringParameters);
	}

	@Override
	protected String getDriver() {
		return PostgresDatabase.JDBC_DRIVER;
	}

	@Override
	protected String getJdbcUrl(InetAddress ipAddress, int port, String database) {
		return
			"jdbc:postgresql://"
			+ ipAddress.toBracketedString()
			+ ":" + port
			+ "/" + database;
	}
}
