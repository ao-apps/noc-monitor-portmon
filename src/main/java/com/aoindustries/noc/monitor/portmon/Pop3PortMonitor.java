/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2001-2013, 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocketFactory;

/**
 * Monitors with POP3-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class Pop3PortMonitor extends DefaultTcpPortMonitor {

  private final URIParameters monitoringParameters;

  /**
   * Creates a new POP3 monitor.
   */
  public Pop3PortMonitor(InetAddress ipAddress, Port port, boolean ssl, URIParameters monitoringParameters) {
    super(ipAddress, port, ssl);
    this.monitoringParameters = monitoringParameters;
  }

  /**
   * Creates a new POP3 monitor.
   */
  public Pop3PortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
    super(ipAddress, port, false);
    this.monitoringParameters = monitoringParameters;
  }

  private static void quit(Reader in, Writer out, StringBuilder buffer) throws IOException {
    out.write("QUIT" + CRLF);
    out.flush();
    String line = readLine(in, buffer);
    if (line == null) {
      throw new EOFException("End of file reading QUIT response");
    }
    if (!line.startsWith("+OK")) {
      throw new IOException("Unexpected line reading QUIT response: " + line);
    }
  }

  @Override
  public String checkPort(Socket socket, InputStream socketIn, OutputStream socketOut) throws Exception {
    Socket sslSocket = null;
    try {
      // Get the configuration
      String username = monitoringParameters.getParameter("username");
      if (username == null || username.length() == 0) {
        throw new IllegalArgumentException("monitoringParameters does not include the username");
      }
      String password = monitoringParameters.getParameter("password");
      if (password == null || password.length() == 0) {
        throw new IllegalArgumentException("monitoringParameters does not include the password");
      }
      boolean starttls =
          // Will not try STARTTLS when is SSL
          !ssl
              // Use SSL unless explicitely disabled with starttls=false
              && !"false".equalsIgnoreCase(monitoringParameters.getParameter("starttls"));

      final StringBuilder buffer = new StringBuilder();
      Charset charset = StandardCharsets.US_ASCII;
      Writer out = new OutputStreamWriter(socketOut, charset);
      try {
        Reader in = new InputStreamReader(socketIn, charset);
        try {
          // Buffer now when not using STARTTLS
          if (!starttls) {
            out = new BufferedWriter(out);
            in = new BufferedReader(in);
          }
          // Status line
          String line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading status");
          }
          if (!line.startsWith("+OK ")) {
            throw new IOException("Unexpected status line: " + line);
          }
          if (starttls) {
            // See https://datatracker.ietf.org/doc/html/rfc2595
            // TODO: CAPA command first (it would add one round-trip)? https://nmap.org/nsedoc/scripts/pop3-capabilities.html
            // STLS
            out.write("STLS" + CRLF);
            out.flush();
            line = readLine(in, buffer);
            if (line == null) {
              throw new EOFException("End of file reading STLS response");
            }
            if (!line.startsWith("+OK ")) {
              // Quit
              quit(in, out, buffer);
              throw new IOException("Unexpected line reading STLS response: " + line);
            }
            // Wrap in SSL
            SSLSocketFactory sslFact = (SSLSocketFactory) SSLSocketFactory.getDefault();
            sslSocket = sslFact.createSocket(socket, ipAddress.toString(), port.getPort(), false);
            out = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), charset));
            in = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), charset));
          }
          // USER
          out.write("USER ");
          out.write(username);
          out.write(CRLF);
          out.flush();
          line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading USER response");
          }
          if (!line.startsWith("+OK ")) {
            throw new IOException("Unexpected line reading USER response: " + line);
          }
          // PASS
          out.write("PASS ");
          out.write(password);
          out.write(CRLF);
          out.flush();
          line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading PASS response");
          }
          String result;
          if (line.startsWith("+OK ")) {
            // Not locked
            result = line.substring(4);
          } else if ("-ERR [IN-USE] Unable to lock maildrop: Mailbox is locked by POP server".equals(line)) {
            // Locked, but otherwise OK
            result = line.substring(5);
          } else {
            throw new IOException("Unexpected line reading PASS response: " + line);
          }
          // Quit
          quit(in, out, buffer);
          // Return OK result
          return result;
        } finally {
          in.close();
        }
      } finally {
        out.close();
      }
    } finally {
      if (sslSocket != null) {
        sslSocket.close();
      }
    }
  }
}
