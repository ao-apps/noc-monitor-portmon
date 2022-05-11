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

import com.aoapps.lang.Strings;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;

/**
 * Monitors with SMTP-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class SmtpPortMonitor extends DefaultTcpPortMonitor {

  private final URIParameters monitoringParameters;

  /**
   * Creates a new SMTP monitor.
   */
  public SmtpPortMonitor(InetAddress ipAddress, Port port, boolean ssl, URIParameters monitoringParameters) {
    super(ipAddress, port, ssl);
    this.monitoringParameters = monitoringParameters;
  }

  /**
   * Creates a new SMTP monitor.
   */
  public SmtpPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
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
    if (!line.startsWith("221 2.0.0 ")) {
      throw new IOException("Unexpected line reading QUIT response: " + line);
    }
  }

  @Override
  public String checkPort(Socket socket, InputStream socketIn, OutputStream socketOut) throws Exception {
    Socket sslSocket = null;
    try {
      // Get the configuration
      String from = Strings.nullIfEmpty(monitoringParameters.getParameter("from"));
      if (from == null) {
        throw new IllegalArgumentException("monitoringParameters does not include the from parameter");
      }
      String recipient = Strings.nullIfEmpty(monitoringParameters.getParameter("recipient"));
      if (recipient == null) {
        throw new IllegalArgumentException("monitoringParameters does not include the recipient parameter");
      }
      boolean starttls =
          // Will not try STARTTLS when is SSL
          !ssl
              // Use SSL unless explicitely disabled with starttls=false
              && !"false".equalsIgnoreCase(monitoringParameters.getParameter("starttls"));
      // Optional for authenticated SMTP
      String username = Strings.nullIfEmpty(monitoringParameters.getParameter("username"));
      String password = Strings.nullIfEmpty(monitoringParameters.getParameter("password"));
      if ((username == null) != (password == null)) {
        throw new IllegalArgumentException("monitoringParameters must include either both username and password or neither");
      }
      // Null character used in AUTH PLAIN protocol below, make sure not in the username/password directly
      if (username != null && username.indexOf('\0') != -1) {
        throw new IllegalArgumentException("monitoringParameters contains illegal null in username");
      }
      if (password != null && password.indexOf('\0') != -1) {
        throw new IllegalArgumentException("monitoringParameters contains illegal null in password");
      }

      final StringBuilder buffer = new StringBuilder();
      Charset charset = StandardCharsets.US_ASCII;
      Writer out = new OutputStreamWriter(socketOut, charset);
      try {
        Reader in = new InputStreamReader(socketIn, charset);
        try {
          // Buffer now when not using STARTTLS
          if (!starttls) {
            in = new BufferedReader(in);
            out = new BufferedWriter(out);
          }
          // Status line
          String line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading status");
          }
          if (!line.startsWith("220 ")) {
            throw new IOException("Unexpected status line: " + line);
          }
          // NOTE: We are assuming ESMTP here
          // EHLO
          out.write("EHLO ");
          out.write(java.net.InetAddress.getLocalHost().getCanonicalHostName());
          out.write(CRLF);
          out.flush();
          List<String> ehloResponse = new ArrayList<>();
          while (true) {
            line = readLine(in, buffer);
            if (line == null) {
              throw new EOFException("End of file reading EHLO response");
            }
            if (line.startsWith("250-")) {
              // With continuation
              ehloResponse.add(line.substring(4));
            } else if (line.startsWith("250 ")) {
              // End of response
              ehloResponse.add(line.substring(4));
              break;
            } else {
              throw new IOException("Unexpected line reading EHLO response: " + line);
            }
          }
          if (starttls) {
            if (!ehloResponse.contains("STARTTLS")) {
              // Quit
              quit(in, out, buffer);
              throw new IOException("Host does not support STARTTLS: " + ehloResponse);
            }
            // STARTTLS
            out.write("STARTTLS" + CRLF);
            out.flush();
            line = readLine(in, buffer);
            if (line == null) {
              throw new EOFException("End of file reading STARTTLS response");
            }
            if (!line.startsWith("220 2.0.0 ")) {
              throw new IOException("Unexpected line reading STARTTLS response: " + line);
            }
            // Wrap in SSL
            SSLSocketFactory sslFact = (SSLSocketFactory) SSLSocketFactory.getDefault();
            sslSocket = sslFact.createSocket(socket, ipAddress.toString(), port.getPort(), false);
            out = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), charset));
            in = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), charset));
          }
          if (username != null) {
            // NOTE: We are assuming AUTH PLAIN here
            // AUTH PLAIN
            out.write("AUTH PLAIN ");
            // See http://www.fehcom.de/qmail/smtpauth.html
            String authMessage = "\0" + username + "\0" + password;
            //out.println(Base64Coder.encode(authMessage.getBytes(charset)));
            out.write(Base64.getEncoder().encodeToString(authMessage.getBytes(charset)));
            out.write(CRLF);
            out.flush();
            line = readLine(in, buffer);
            if (line == null) {
              throw new EOFException("End of file reading AUTH PLAIN response");
            }
            if (
                !line.startsWith("235 2.0.0 ")
                    && !line.startsWith("235 2.7.0 ")
            ) {
              throw new IOException("Unexpected line reading AUTH PLAIN response: " + line);
            }
          }
          // MAIL From
          out.write("MAIL From:");
          out.write(from);
          out.write(CRLF);
          out.flush();
          line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading MAIL From response");
          }
          if (!line.startsWith("250 2.1.0 ")) {
            throw new IOException("Unexpected line reading MAIL From response: " + line);
          }
          // RCPT To
          out.write("RCPT To:");
          out.write(recipient);
          out.write(CRLF);
          out.flush();
          line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading RCPT To response");
          }
          if (!line.startsWith("250 2.1.5 ")) {
            throw new IOException("Unexpected line reading RCPT To response: " + line);
          }
          // DATA
          out.write("DATA" + CRLF);
          out.flush();
          line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading DATA response");
          }
          if (!line.startsWith("354 ")) {
            throw new IOException("Unexpected line reading DATA response: " + line);
          }
          // Message headers and body
          out.write("To: ");
          out.write(recipient);
          out.write(CRLF);
          out.write("From: ");
          out.write(from);
          out.write(CRLF);
          out.write("Subject: SMTP monitoring message" + CRLF);
          out.write(CRLF);
          out.write("This message is generated for SMTP port monitoring." + CRLF);
          out.write("." + CRLF);
          out.flush();
          line = readLine(in, buffer);
          if (line == null) {
            throw new EOFException("End of file reading DATA response");
          }
          if (!line.startsWith("250 2.0.0 ")) {
            throw new IOException("Unexpected line reading DATA response: " + line);
          }
          String result = line.substring(10);
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
