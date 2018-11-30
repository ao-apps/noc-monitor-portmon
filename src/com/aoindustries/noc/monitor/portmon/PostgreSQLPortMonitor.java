/*
 * Copyright 2009-2013, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.aoserv.client.postgresql.Database;
import com.aoindustries.aoserv.client.postgresql.Server;
import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Monitors a PostgreSQL database.
 *
 * @author  AO Industries, Inc.
 */
public class PostgreSQLPortMonitor extends JdbcPortMonitor {

	private static final String APPLICATION_NAME = "noc-monitor";

	private static final String APPLICATION_NAME_URL_ENCODED;
	static {
		final String ENCODING = "UTF-8";
		try {
			APPLICATION_NAME_URL_ENCODED = URLEncoder.encode(APPLICATION_NAME, ENCODING);
		} catch(UnsupportedEncodingException e) {
			throw new AssertionError("Encoding " + ENCODING + " should always be valid", e);
		}
	}

	private final boolean ssl;

	public PostgreSQLPortMonitor(InetAddress ipAddress, Port port, HttpParameters monitoringParameters) {
		super(ipAddress, port, monitoringParameters);
		if(ipAddress.isLoopback()) {
			// Do not use SSL unless explicitely enabled with ssl=true
			ssl = "true".equalsIgnoreCase(monitoringParameters.getParameter("ssl"));
		} else {
			// Use SSL unless explicitely disabled with ssl=false
			ssl = !"false".equalsIgnoreCase(monitoringParameters.getParameter("ssl"));
		}
	}

	@Override
	protected String getDriver() {
		return Database.JDBC_DRIVER;
	}

	/**
	 * See <a href="https://jdbc.postgresql.org/documentation/94/connect.html">https://jdbc.postgresql.org/documentation/94/connect.html</a>
	 */
	@Override
	protected String getJdbcUrl(InetAddress ipAddress, int port, String database) {
		StringBuilder jdbcUrl = new StringBuilder();
		jdbcUrl
			.append("jdbc:postgresql://")
			.append(ipAddress.toBracketedString());
		if(port != Server.DEFAULT_PORT.getPort()) {
			jdbcUrl.append(':').append(port);
		}
		jdbcUrl
			.append('/').append(database)
			.append("?loginTimeout=").append(TIMEOUT)
			.append("&connectTimeout=").append(TIMEOUT)
			.append("&socketTimeout=").append(TIMEOUT)
			.append("&tcpKeepAlive=true")
			.append("&ApplicationName=").append(APPLICATION_NAME_URL_ENCODED)
			// Set on each connection: .append("&readOnly=").append(readOnly);
		;
		if(ssl) jdbcUrl.append("&ssl=true");
		return jdbcUrl.toString();
	}
}
