/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2009-2013, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-portmon.
 *
 * noc-monitor-portmon is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-portmon is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-portmon.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.monitor.portmon;

import com.aoapps.lang.util.ErrorPrinter;
import com.aoapps.net.InetAddress;
import com.aoapps.net.Port;
import com.aoapps.net.URIParameters;
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
public abstract class JdbcPortMonitor extends PortMonitor {

  private static final Logger logger = Logger.getLogger(JdbcPortMonitor.class.getName());

  private static final ConcurrentMap<String, Class<?>> driversLoaded = new ConcurrentHashMap<>();

  /**
   * Loads a driver at most once.
   */
  private static void loadDriver(String classname) throws ClassNotFoundException {
    if (!driversLoaded.containsKey(classname)) {
      Class<?> driver = Class.forName(classname);
      driversLoaded.putIfAbsent(classname, driver);
    }
  }

  protected static final int TIMEOUT = DefaultTcpPortMonitor.TIMEOUT;

  private final URIParameters monitoringParameters;

  protected final boolean readOnly;

  /**
   * Creates a new JDBC monitor.
   */
  protected JdbcPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
    super(ipAddress, port);
    this.monitoringParameters = monitoringParameters;
    // Is read-only unless explicitely disabled with readOnly=false
    readOnly = !"false".equalsIgnoreCase(monitoringParameters.getParameter("readOnly"));
  }

  private volatile Connection conn;

  @Override
  public final String checkPort() throws Exception {
    // Get the configuration
    String username = monitoringParameters.getParameter("username");
    if (username == null || username.length() == 0) {
      username = getDefaultUsername();
    }
    String password = monitoringParameters.getParameter("password");
    if (password == null || password.length() == 0) {
      throw new IllegalArgumentException("monitoringParameters does not include the password parameter");
    }
    String database = monitoringParameters.getParameter("database");
    if (database == null || database.length() == 0) {
      database = getDefaultDatabase();
    }
    String query = monitoringParameters.getParameter("query");
    if (query == null || query.length() == 0) {
      query = getDefaultQuery();
    }

    loadDriver(getDriver());
    conn = DriverManager.getConnection(
        getJdbcUrl(ipAddress, port.getPort(), database),
        username,
        password
    );
    try {
      conn.setReadOnly(readOnly);
      @SuppressWarnings("UnusedAssignment")
      String currentSql = null;
      try (
          Statement stmt = conn.createStatement();
          ResultSet results = stmt.executeQuery(currentSql = query)
          ) {
        if (!results.next()) {
          throw new SQLException("No row returned"); // TODO: NoRowException move to ao-sql
        }
        ResultSetMetaData metaData = results.getMetaData();
        int colCount = metaData.getColumnCount();
        if (colCount == 0) {
          throw new SQLException("No columns returned"); // TODO: Create a NoColumnException in ao-sql
        }
        if (colCount > 1) {
          throw new SQLException("More than one column returned"); // TODO: Create an ExtraRowException in ao-sql
        }
        String result = results.getString(1);
        if (results.next()) {
          throw new SQLException("More than one row returned"); // TODO: ExtraRowException move to ao-sql
        }
        return result;
      } catch (Error | RuntimeException | SQLException e) {
        ErrorPrinter.addSql(e, currentSql);
        throw e;
      }
    } finally {
      conn.close();
    }
  }

  @Override
  public void cancel() {
    super.cancel();
    Connection myConn = conn;
    if (myConn != null) {
      try {
        myConn.close();
      } catch (SQLException err) {
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
