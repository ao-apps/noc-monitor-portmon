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
 * Monitors with IMAP-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class ImapPortMonitor extends DefaultTcpPortMonitor {

	private final HttpParameters monitoringParameters;

	public ImapPortMonitor(InetAddress ipAddress, Port port, HttpParameters monitoringParameters) {
		super(ipAddress, port);
		this.monitoringParameters = monitoringParameters;
	}

	/**
	 * Will not try STARTTLS when is SSL.
	 */
	protected boolean isSsl() {
		return false;
	}

	/**
	 * Unique tags used in protocol.
	 */
	private static final String
		TAG_STARTTLS = "AA",
		TAG_LOGIN    = "AB",
		TAG_LOGOUT   = "AC";

	private static void logout(Reader in, Writer out, StringBuilder buffer) throws IOException {
		out.write(TAG_LOGOUT + " LOGOUT" + CRLF);
		out.flush();
		String line = readLine(in, buffer);
		if(line==null) throw new EOFException("End of file reading logout response line 1");
		if(!line.startsWith("* BYE") && !line.startsWith(TAG_LOGOUT + " OK LOGOUT")) throw new IOException("Unexpected line reading logout response line 1: "+line);
		//if(line==null) throw new EOFException("End of file reading logout response line 2");
		//if(!line.startsWith("* BYE") && !line.startsWith(TAG_LOGOUT + " OK LOGOUT")) throw new IOException("Unexpected line reading logout response line 2: "+line);
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
					// Capabilities
					String line = readLine(in, buffer);
					if(line==null) throw new EOFException("End of file reading capabilities");
					int bracketPos = line.indexOf(']');
					final String CAP1 = "* OK [CAPABILITY ";
					if(!line.startsWith(CAP1) || bracketPos==-1) throw new IOException("Unexpected capabilities line: "+line);
					if(starttls) {
						// See https://tools.ietf.org/html/rfc2595
						final String capability = line.substring(
							// Include first space of capabilities always
							CAP1.length() - 1,
							bracketPos
						);
						if(
							!capability.contains(" STARTTLS ")
							&& !capability.endsWith(" STARTTLS")
						) {
							// Logout
							logout(in, out, buffer);
							throw new IOException("Server does not support STARTTLS: " + capability);
						}
						// STARTTLS
						out.write(TAG_STARTTLS + " STARTTLS" + CRLF);
						out.flush();
						line = readLine(in, buffer);
						if(line == null) throw new EOFException("End of file reading STARTTLS response");
						if(!line.startsWith(TAG_STARTTLS + " OK ")) throw new IOException("Unexpected line reading STARTTLS response: " + line);
						// Wrap in SSL
						SSLSocketFactory sslFact = (SSLSocketFactory)SSLSocketFactory.getDefault();
						sslSocket = sslFact.createSocket(socket, ipAddress.toString(), port.getPort(), false);
						out = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), charset));
						in = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), charset));
					}
					// Login
					out.write(TAG_LOGIN + " LOGIN ");
					out.write(username);
					out.write(" \"");
					out.write(password);
					out.write("\"" + CRLF);
					out.flush();
					line = readLine(in, buffer);
					if(line==null) throw new EOFException("End of file reading login response");
					bracketPos = line.indexOf(']');
					if(!line.startsWith(TAG_LOGIN + " OK [CAPABILITY ") || bracketPos==-1) throw new IOException("Unexpected line reading login response: "+line);
					String result = line.substring(bracketPos+1).trim();
					// Logout
					logout(in, out, buffer);
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
