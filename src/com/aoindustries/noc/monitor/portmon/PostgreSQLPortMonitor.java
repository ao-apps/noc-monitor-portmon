/*
 * Copyright 2009-2013, 2016, 2017, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.aoserv.client.postgresql.Database;
import com.aoindustries.aoserv.client.postgresql.Server;
import com.aoindustries.aoserv.client.postgresql.User;
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

	private static final String ENCODING = "UTF-8";

	private static final String APPLICATION_NAME = "noc-monitor";

	/**
	 * By default, validate the certificate, but do not verify the hostname.
	 * This is necessary because we connect by IP address in the JDBC URL.
	 * <p>
	 * See <a href="https://jdbc.postgresql.org/documentation/head/connect.html">Connecting to the Database</a>.
	 * </p>
	 */
	private static final String DEFAULT_SSLMODE = "verify-ca";

	/**
	 * See <a href="https://github.com/pgjdbc/pgjdbc/issues/1307">driver 42.2.5 does not recognize cacerts of JRE · Issue #1307 · pgjdbc/pgjdbc</a>.
	 */
	private static final String DEFAULT_SSL_FACTORY = "org.postgresql.ssl.DefaultJavaSSLFactory";

	private static String encode(String value) {
		if(value == null) return null;
		try {
			return URLEncoder.encode(value, ENCODING);
		} catch(UnsupportedEncodingException e) {
			throw new AssertionError("Encoding " + ENCODING + " should always be valid", e);
		}
	}

	private final boolean ssl;
	private final String sslmode;
	private final String sslfactory;

	public PostgreSQLPortMonitor(InetAddress ipAddress, Port port, HttpParameters monitoringParameters) {
		super(ipAddress, port, monitoringParameters);
		if(ipAddress.isLoopback()) {
			// Do not use SSL unless explicitely enabled with ssl=true
			ssl = "true".equalsIgnoreCase(monitoringParameters.getParameter("ssl"));
		} else {
			// Use SSL unless explicitely disabled with ssl=false
			ssl = !"false".equalsIgnoreCase(monitoringParameters.getParameter("ssl"));
		}
		if(ssl) {
			String sslmode = monitoringParameters.getParameter("sslmode");
			if(sslmode == null) sslmode = DEFAULT_SSLMODE;
			this.sslmode = sslmode;

			String sslfactory = monitoringParameters.getParameter("sslfactory");
			if(sslfactory == null) sslfactory = DEFAULT_SSL_FACTORY;
			this.sslfactory = sslfactory;
		} else {
			sslmode = null;
			this.sslfactory = null;
		}
	}

	@Override
	protected String getDriver() {
		return Database.JDBC_DRIVER;
	}

	/**
	 * See <a href="https://jdbc.postgresql.org/documentation/head/connect.html">Connecting to the Database</a>
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
			.append('/').append(encode(database))
			.append("?loginTimeout=").append(encode(Integer.toString(TIMEOUT)))
			.append("&connectTimeout=").append(encode(Integer.toString(TIMEOUT)))
			.append("&socketTimeout=").append(encode(Integer.toString(TIMEOUT)))
			.append("&tcpKeepAlive=true")
			.append("&ApplicationName=").append(encode(APPLICATION_NAME))
			// Set on each connection: .append("&readOnly=").append(encode(Boolean.toString(readOnly)));
		;
		if(ssl) {
			jdbcUrl.append("&ssl=true");
			if(sslmode != null && !sslmode.isEmpty()) {
				jdbcUrl.append("&sslmode=").append(encode(sslmode));
			}
			if(sslfactory != null && !sslfactory.isEmpty()) {
				jdbcUrl.append("&sslfactory=").append(encode(sslfactory));
			}
		}
		return jdbcUrl.toString();
	}

	@Override
	protected String getDefaultUsername() {
		return User.POSTGRESMON.toString();
	}

	@Override
	protected String getDefaultDatabase() {
		return Database.POSTGRESMON.toString();
	}
}
