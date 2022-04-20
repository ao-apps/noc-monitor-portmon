/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Monitors any SSL port by simply connecting and disconnecting.
 *
 * @author  AO Industries, Inc.
 */
public class DefaultSslPortMonitor extends DefaultTcpPortMonitor {

  public DefaultSslPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
    super(
      ipAddress,
      port,
      // Use SSL unless explicitely disabled with ssl=false
      !"false".equalsIgnoreCase(monitoringParameters.getParameter("ssl"))
    );
  }

  protected static final String CONNECTED_SUCCESSFULLY_SSL_DISABLED = CONNECTED_SUCCESSFULLY + " (SSL disabled)";

  @Override
  public String checkPort(Socket socket, InputStream socketIn, OutputStream socketOut) throws Exception {
    if (ssl) {
      return CONNECTED_SUCCESSFULLY_SSL;
    } else {
      return CONNECTED_SUCCESSFULLY_SSL_DISABLED;
    }
  }
}
