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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Monitors with FTP-specific protocol support.
 *
 * <p>TODO: Support "AUTH TLS" for FTP monitoring</p>
 *
 * @author  AO Industries, Inc.
 */
public class FtpPortMonitor extends DefaultTcpPortMonitor {

  private final URIParameters monitoringParameters;

  /**
   * Creates a new FTP monitor.
   */
  public FtpPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
    super(ipAddress, port, false);
    this.monitoringParameters = monitoringParameters;
  }

  @Override
  public String checkPort(Socket socket, InputStream socketIn, OutputStream socketOut) throws Exception {
    // Get the configuration
    String username = monitoringParameters.getParameter("username");
    if (username == null || username.length() == 0) {
      throw new IllegalArgumentException("monitoringParameters does not include the username");
    }
    String password = monitoringParameters.getParameter("password");
    if (password == null || password.length() == 0) {
      throw new IllegalArgumentException("monitoringParameters does not include the password");
    }

    Charset charset = StandardCharsets.US_ASCII;
    try (
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socketOut, charset));
        BufferedReader in = new BufferedReader(new InputStreamReader(socketIn, charset))
        ) {
      // Status line
      String line = in.readLine();
      if (line == null) {
        throw new EOFException("End of file reading status");
      }
      if (!line.startsWith("220 ")) {
        throw new IOException("Unexpected status line: " + line);
      }
      // User
      out.write("user ");
      out.write(username);
      out.write(CRLF);
      out.flush();
      line = in.readLine();
      if (line == null) {
        throw new EOFException("End of file reading user response");
      }
      if (!line.startsWith("331 ")) {
        throw new IOException("Unexpected line reading user response: " + line);
      }
      // Pass
      out.write("pass ");
      out.write(password);
      out.write(CRLF);
      out.flush();
      line = in.readLine();
      if (line == null) {
        throw new EOFException("End of file reading pass response");
      }
      if (!line.startsWith("230 ")) {
        throw new IOException("Unexpected line reading pass response: " + line);
      }
      final String result = line.substring(4);
      // Quit
      out.write("quit" + CRLF);
      out.flush();
      line = in.readLine();
      if (line == null) {
        throw new EOFException("End of file reading quit response");
      }
      if (!line.startsWith("221 ")) {
        throw new IOException("Unexpected line reading quit response: " + line);
      }
      while ((line = in.readLine()) != null) {
        if (!line.startsWith("221 ")) {
          throw new IOException("Unexpected line reading quit response: " + line);
        }
      }
      // Return OK result
      return result;
    }
  }
}
