/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2001-2013, 2016, 2017, 2018, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.hodgepodge.io.AOPool;
import com.aoapps.net.InetAddress;
import com.aoapps.net.Port;
import com.aoapps.net.Protocol;
import com.aoapps.net.URIParameters;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocketFactory;

/**
 * Monitors any TCP port by simply connecting and disconnecting.  Additional
 * protocol-specific checks are performed by subclasses overriding the checkPort
 * method.
 *
 * @author  AO Industries, Inc.
 */
public class DefaultTcpPortMonitor extends PortMonitor {

  private static final Logger logger = Logger.getLogger(DefaultTcpPortMonitor.class.getName());

  protected static final int TIMEOUT = 60_000;

  protected final boolean ssl;

  private volatile Socket socket;

  /**
   * Creates a new default TCP monitor.
   */
  public DefaultTcpPortMonitor(InetAddress ipAddress, Port port, boolean ssl) {
    super(ipAddress, port);
    if (port.getProtocol() != Protocol.TCP) {
      throw new IllegalArgumentException("port not TCP: " + port);
    }
    this.ssl = ssl;
  }

  /**
   * Creates a new default TCP monitor.
   */
  public DefaultTcpPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
    this(
        ipAddress,
        port,
        // Do not use SSL unless explicitely enabled with ssl=true
        Boolean.parseBoolean(monitoringParameters.getParameter("ssl"))
    );
  }

  @Override
  public void cancel() {
    super.cancel();
    Socket mySocket = socket;
    if (mySocket != null) {
      try {
        mySocket.close();
      } catch (IOException err) {
        logger.log(Level.WARNING, null, err);
      }
    }
  }

  /**
   * Gets the socket to use for this port connection.
   */
  protected Socket connect() throws Exception {
    boolean successful = false;
    Socket s = new Socket();
    try {
      s.setKeepAlive(true);
      s.setSoLinger(true, AOPool.DEFAULT_SOCKET_SO_LINGER);
      //s.setTcpNoDelay(true);
      s.setSoTimeout(TIMEOUT);
      s.connect(new InetSocketAddress(ipAddress.toString(), port.getPort()), TIMEOUT);
      successful = true;
      if (ssl) {
        SSLSocketFactory sslFact = (SSLSocketFactory) SSLSocketFactory.getDefault();
        s = sslFact.createSocket(s, ipAddress.toString(), port.getPort(), true);
      }
      return s;
    } finally {
      if (!successful) {
        s.close();
      }
    }
  }

  protected static final String CONNECTED_SUCCESSFULLY = "Connected successfully";
  protected static final String CONNECTED_SUCCESSFULLY_SSL = CONNECTED_SUCCESSFULLY + " over SSL";

  @Override
  public final String checkPort() throws Exception {
    socket = connect();
    try {
      return checkPort(socket, socket.getInputStream(), socket.getOutputStream());
    } finally {
      socket.close();
    }
  }

  /**
   * Performs any protocol-specific monitoring.  This default implementation does
   * nothing.
   */
  protected String checkPort(Socket socket, InputStream in, OutputStream out) throws Exception {
    if (ssl) {
      return CONNECTED_SUCCESSFULLY_SSL;
    } else {
      return CONNECTED_SUCCESSFULLY;
    }
  }
}
