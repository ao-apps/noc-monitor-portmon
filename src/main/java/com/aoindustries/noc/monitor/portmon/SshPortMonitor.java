/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2001-2013, 2016, 2017, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Monitors with SSH-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class SshPortMonitor extends DefaultTcpPortMonitor {

  public SshPortMonitor(InetAddress ipAddress, Port port) {
    super(ipAddress, port, false);
  }

  @Override
  public String checkPort(Socket socket, InputStream socketIn, OutputStream socketOut) throws Exception {
    Charset charset = StandardCharsets.US_ASCII;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(socketIn, charset))) {
      // Status line
      String line = in.readLine();
      if (line == null) {
        throw new EOFException("End of file reading status");
      }
      if (!line.startsWith("SSH-")) {
        throw new IOException("Unexpected status line: " + line);
      }
      // Return OK result
      return line;
    }
  }
}
