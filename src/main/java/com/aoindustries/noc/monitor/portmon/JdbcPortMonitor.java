/*
 * Copyright 2009-2013, 2016, 2017, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserve parameterd.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.net.URIParameters;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors a database over JDBC.
 *
 * @author  AO Industries, Inc.
 */
abstract public class JdbcPortMonitor extends PortMonitor {

	private static final Logger logger = Logger.getLogger(JdbcPortMonitor.class.getName());

	private static final ConcurrentMap<String,Object> driversLoaded = new ConcurrentHashMap<>();

	/**
	 * Loads a driver at most once.
	 */
	private static void loadDriver(String classname) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		if(!driversLoaded.containsKey(classname)) {
			Object O = Class.forName(classname).newInstance();
			driversLoaded.putIfAbsent(classname, O);
		}
	}

	protected static final int TIMEOUT = DefaultTcpPortMonitor.TIMEOUT;

	private final URIParameters monitoringParameters;

	protected final boolean readOnly;

	public JdbcPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
		super(ipAddress, port);
		this.monitoringParameters = monitoringParameters;
		// Is read-only unless explicitely disabled with readOnly=false
		readOnly = !"false".equalsIgnoreCase(monitoringParameters.getParameter("readOnly"));
	}

	private volatile Connection conn;

	@Override
	final public String checkPort() throws Exception {
		// Get the configuration
		String username = monitoringParameters.getParameter("username");
		if(username==null || username.length()==0) username = getDefaultUsername();
		String password = monitoringParameters.getParameter("password");
		if(password==null || password.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the password parameter");
		String database = monitoringParameters.getParameter("database");
		if(database==null || database.length()==0) database = getDefaultDatabase();
		String query = monitoringParameters.getParameter("query");
		if(query==null || query.length()==0) query = getDefaultQuery();

		loadDriver(getDriver());
		conn = DriverManager.getConnection(
			getJdbcUrl(ipAddress, port.getPort(), database),
			username,
			password
		);
		try {
			conn.setReadOnly(readOnly);
			try (
				Statement stmt = conn.createStatement();
				ResultSet results = stmt.executeQuery(query)
			) {
				if(!results.next()) throw new SQLException("No row returned");
				ResultSetMetaData metaData = results.getMetaData();
				int colCount = metaData.getColumnCount();
				if(colCount==0) throw new SQLException("No columns returned");
				if(colCount>1) throw new SQLException("More than one column returned");
				String result = results.getString(1);
				if(results.next()) throw new SQLException("More than one row returned");
				return result;
			}
		} finally {
			conn.close();
		}
	}

	@Override
	public void cancel() {
		super.cancel();
		Connection myConn = conn;
		if(myConn!=null) {
			try {
				myConn.close();
			} catch(SQLException err) {
				logger.log(Level.WARNING, null, err);
			}
		}
	}

	/**
	 * Gets the driver classname.
	 */
	protected abstract String getDriver();

	/**
	 * Generates the JDBC URL.
	 */
	protected abstract String getJdbcUrl(InetAddress ipAddress, int port, String database);

	protected abstract String getDefaultUsername();

	protected abstract String getDefaultDatabase();

	protected String getDefaultQuery() {
		return "select 1";
	}
}
