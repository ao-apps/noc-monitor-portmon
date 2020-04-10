/*
 * Copyright 2009-2013, 2016, 2017, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.aoserv.client.postgresql.Database;
import com.aoindustries.aoserv.client.postgresql.Server;
import com.aoindustries.aoserv.client.postgresql.User;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.net.URIEncoder;
import com.aoindustries.net.URIParameters;

/**
 * Monitors a PostgreSQL database.
 *
 * @author  AO Industries, Inc.
 */
public class PostgreSQLPortMonitor extends JdbcPortMonitor {

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

	private final boolean ssl;
	private final String sslmode;
	private final String sslfactory;

	public PostgreSQLPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
		super(ipAddress, port, monitoringParameters);
		if(ipAddress.isLoopback()) {
			// Do not use SSL unless explicitely enabled with ssl=true
			ssl = "true".equalsIgnoreCase(monitoringParameters.getParameter("ssl"));
		} else {
			// Use SSL unless explicitely disabled with ssl=false
			ssl = !"false".equalsIgnoreCase(monitoringParameters.getParameter("ssl"));
		}
		if(ssl) {
			String _sslmode = monitoringParameters.getParameter("sslmode");
			if(_sslmode == null) _sslmode = DEFAULT_SSLMODE;
			this.sslmode = _sslmode;

			String _sslfactory = monitoringParameters.getParameter("sslfactory");
			if(_sslfactory == null) _sslfactory = DEFAULT_SSL_FACTORY;
			this.sslfactory = _sslfactory;
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
		jdbcUrl.append('/');
		URIEncoder.encodeURIComponent(database, jdbcUrl);
		jdbcUrl.append("?loginTimeout=");
		URIEncoder.encodeURIComponent(Integer.toString(TIMEOUT), jdbcUrl);
		jdbcUrl.append("&connectTimeout=");
		URIEncoder.encodeURIComponent(Integer.toString(TIMEOUT), jdbcUrl);
		jdbcUrl.append("&socketTimeout=");
		URIEncoder.encodeURIComponent(Integer.toString(TIMEOUT), jdbcUrl);
		jdbcUrl.append("&tcpKeepAlive=true");
		jdbcUrl.append("&ApplicationName=");
		URIEncoder.encodeURIComponent(APPLICATION_NAME, jdbcUrl);
		// Set on each connection: .append("&readOnly=").append(encode(Boolean.toString(readOnly)));
		if(ssl) {
			jdbcUrl.append("&ssl=true");
			if(sslmode != null && !sslmode.isEmpty()) {
				jdbcUrl.append("&sslmode=");
				URIEncoder.encodeURIComponent(sslmode, jdbcUrl);
			}
			if(sslfactory != null && !sslfactory.isEmpty()) {
				jdbcUrl.append("&sslfactory=");
				URIEncoder.encodeURIComponent(sslfactory, jdbcUrl);
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
