/*
 * Copyright 2009-2013, 2016, 2017 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;

/**
 * Monitors a MySQL database.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLPortMonitor extends JdbcPortMonitor {

	public MySQLPortMonitor(InetAddress ipAddress, int port, HttpParameters monitoringParameters) {
		super(ipAddress, port, monitoringParameters);
	}

	@Override
	protected String getDriver() {
		return "com.mysql.jdbc.Driver";
	}

	@Override
	protected String getJdbcUrl(InetAddress ipAddress, int port, String database) {
		return
			"jdbc:mysql://"
			+ ipAddress.toBracketedString()
			+ ":" + port
			+ "/" + database;
	}
}
