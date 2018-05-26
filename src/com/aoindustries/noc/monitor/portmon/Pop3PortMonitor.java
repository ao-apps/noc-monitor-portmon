/*
 * Copyright 2001-2013, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
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
import javax.net.ssl.SSLSocketFactory;

/**
 * Monitors with POP3-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class Pop3PortMonitor extends DefaultTcpPortMonitor {

	private final HttpParameters monitoringParameters;

	public Pop3PortMonitor(InetAddress ipAddress, Port port, HttpParameters monitoringParameters) {
		super(ipAddress, port);
		this.monitoringParameters = monitoringParameters;
	}

	/**
	 * Will not try STARTTLS when is SSL.
	 */
	protected boolean isSsl() {
		return false;
	}

	private static void quit(Reader in, Writer out, StringBuilder buffer) throws IOException {
		out.write("QUIT" + CRLF);
		out.flush();
		String line = readLine(in, buffer);
		if(line==null) throw new EOFException("End of file reading QUIT response");
		if(!line.startsWith("+OK")) throw new IOException("Unexpected line reading QUIT response: "+line);
	}

	@Override
	public String checkPort(Socket socket, InputStream socketIn, OutputStream socketOut) throws Exception {
		Socket sslSocket = null;
		try {
			// Get the configuration
			String username = monitoringParameters.getParameter("username");
			if(username==null || username.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the username");
			String password = monitoringParameters.getParameter("password");
			if(password==null || password.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the password");
			// Use SSL unless explicitely disabled with starttls=false
			boolean starttls = !isSsl() && !"false".equalsIgnoreCase(monitoringParameters.getParameter("starttls"));

			final StringBuilder buffer = new StringBuilder();
			Charset charset = Charset.forName("US-ASCII");
			Writer out = new OutputStreamWriter(socketOut, charset);
			try {
				Reader in = new InputStreamReader(socketIn, charset);
				try {
					// Buffer now when not using STARTTLS
					if(!starttls) {
						out = new BufferedWriter(out);
						in = new BufferedReader(in);
					}
					// Status line
					String line = readLine(in, buffer);
					if(line==null) throw new EOFException("End of file reading status");
					if(!line.startsWith("+OK ")) throw new IOException("Unexpected status line: "+line);
					if(starttls) {
						// See https://tools.ietf.org/html/rfc2595
						// TODO: CAPA command first (it would add one round-trip)? https://nmap.org/nsedoc/scripts/pop3-capabilities.html
						// STLS
						out.write("STLS" + CRLF);
						out.flush();
						line = readLine(in, buffer);
						if(line==null) throw new EOFException("End of file reading STLS response");
						if(!line.startsWith("+OK ")) {
							// Quit
							quit(in, out, buffer);
							throw new IOException("Unexpected line reading STLS response: "+line);
						}
						// Wrap in SSL
						SSLSocketFactory sslFact = (SSLSocketFactory)SSLSocketFactory.getDefault();
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
					if(line==null) throw new EOFException("End of file reading USER response");
					if(!line.startsWith("+OK ")) throw new IOException("Unexpected line reading USER response: "+line);
					// PASS
					out.write("PASS ");
					out.write(password);
					out.write(CRLF);
					out.flush();
					line = readLine(in, buffer);
					if(line==null) throw new EOFException("End of file reading PASS response");
					String result;
					if(line.startsWith("+OK ")) {
						// Not locked
						result = line.substring(4);
					} else if(line.equals("-ERR [IN-USE] Unable to lock maildrop: Mailbox is locked by POP server")) {
						// Locked, but otherwise OK
						result = line.substring(5);
					} else {
						throw new IOException("Unexpected line reading PASS response: "+line);
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
			if(sslSocket != null) sslSocket.close();
		}
	}
}
