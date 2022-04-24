/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2001-2009, 2016, 2017, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.net.Port;
import com.aoapps.net.Protocol;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Monitors any UDP port.
 *
 * @author  AO Industries, Inc.
 */
public class DefaultUdpPortMonitor extends PortMonitor {

  private volatile DatagramSocket datagramSocket;

  public DefaultUdpPortMonitor(com.aoapps.net.InetAddress ipAddress, Port port) {
    super(ipAddress, port);
    if (port.getProtocol() != Protocol.UDP) {
      throw new IllegalArgumentException("port not UDP: " + port);
    }
  }

  @Override
  public void cancel() {
    super.cancel();
    DatagramSocket myDatagramSocket = datagramSocket;
    if (myDatagramSocket != null) {
      myDatagramSocket.close();
    }
  }

  @Override
  public String checkPort() throws Exception {
    datagramSocket = new DatagramSocket();
    try {
      datagramSocket.connect(InetAddress.getByName(ipAddress.toString()), port.getPort());
    } finally {
      // datagramSocket.disconnect();
      datagramSocket.close();
    }
    return DefaultTcpPortMonitor.CONNECTED_SUCCESSFULLY;
  }
}
