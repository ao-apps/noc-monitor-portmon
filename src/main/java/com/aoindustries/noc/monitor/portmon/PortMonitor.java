/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2001-2013, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.net.InetAddress;
import com.aoapps.net.Port;
import com.aoapps.net.URIParameters;
import com.aoindustries.aoserv.client.net.AppProtocol;
import java.io.IOException;
import java.io.Reader;

/**
 * A <code>PortMonitor</code> connects to a service on a port and verifies it is
 * working correctly.  The monitor will only be used at most once per instance.
 * <p>
 * TODO: Support chronyd protocol: chronyc tracking
 * </p>
 *
 * @author  AO Industries, Inc.
 */
public abstract class PortMonitor {

  /**
   * Factory method to get the best port monitor for the provided port
   * details.  If can't find any monitor, will through IllegalArgumentException.
   */
  public static PortMonitor getPortMonitor(InetAddress ipAddress, Port port, String appProtocol, URIParameters monitoringParameters) throws IllegalArgumentException {
    com.aoapps.net.Protocol netProtocol = port.getProtocol();
    switch (netProtocol) {
      case UDP:
        // UDP
        return new DefaultUdpPortMonitor(ipAddress, port);
      case TCP:
        // TCP
        // TODO: HTTP(S) protocol support, with application-defined criteria
        if (
            AppProtocol.AOSERV_DAEMON_SSL.equals(appProtocol)
                || AppProtocol.AOSERV_MASTER_SSL.equals(appProtocol)
                || AppProtocol.HTTPS.equals(appProtocol)
        ) {
          return new DefaultSslPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.FTP.equals(appProtocol)) {
          return new FtpPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.IMAP2.equals(appProtocol)) {
          return new ImapPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.SIMAP.equals(appProtocol)) {
          return new SimapPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.MYSQL.equals(appProtocol)) {
          return new MysqlPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.POP3.equals(appProtocol)) {
          return new Pop3PortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.SPOP3.equals(appProtocol)) {
          return new Spop3PortMonitor(ipAddress, port, monitoringParameters);
        }
        if (
            AppProtocol.POSTGRESQL.equals(appProtocol)
                // PostgreSQL performs IDENT-based authentication on loopback,
                // ncan't monitor with arbitrary usernames/passwords
                && !ipAddress.isLoopback()
        ) {
          return new PostgresqlPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.SMTP.equals(appProtocol) || AppProtocol.SUBMISSION.equals(appProtocol)) {
          return new SmtpPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.SMTPS.equals(appProtocol)) {
          return new SmtpsPortMonitor(ipAddress, port, monitoringParameters);
        }
        if (AppProtocol.SSH.equals(appProtocol)) {
          return new SshPortMonitor(ipAddress, port);
        }
        return new DefaultTcpPortMonitor(ipAddress, port, monitoringParameters);
      default:
        throw new IllegalArgumentException("Unable to find port monitor: ipAddress=\"" + ipAddress + "\", port=" + port + ", appProtocol=\"" + appProtocol + "\"");
    }
  }

  /**
   * Reads a line into the given buffer.
   */
  protected static String readLine(Reader in, StringBuilder buffer) throws IOException {
    buffer.setLength(0);
    while (true) {
      int ch = in.read();
      if (ch == -1) {
        if (buffer.length() == 0) {
          return null;
        }
        break;
      }
      if (ch == '\n') {
        break;
      }
      if (ch != '\r') {
        buffer.append((char) ch);
      }
    }
    String s = buffer.toString();
    buffer.setLength(0);
    return s;
  }

  protected static final String CRLF = "\r\n";

  protected final InetAddress ipAddress;
  protected final Port port;
  protected volatile boolean canceled;

  /**
   * Creates a new port monitor.
   */
  protected PortMonitor(InetAddress ipAddress, Port port) {
    this.ipAddress = ipAddress;
    this.port = port;
  }

  /**
   * <p>
   * Cancels this port method on a best effort basis.  This will not necessarily cause the checkPort
   * method to return immediately.  This should only be used once the result
   * of checkPort is no longer relevant, such as after a timeout.  Some monitors
   * may still perform their task arbitrarily long after cancel has been called.
   * </p>
   * <p>
   * It is critical that subclass implementations of this method not block in any way.
   * </p>
   *
   * @see  #checkPort()
   */
  public void cancel() {
    canceled = true;
  }

  /**
   * Checks the port.  This may take arbitrarily long to complete, and any timeout
   * should be provided externally and call the <code>cancel</code> method.
   * If any error occurs, must throw an exception.
   *
   * @see  #cancel()
   *
   * @return  the message indicating success
   */
  public abstract String checkPort() throws Exception;
}
