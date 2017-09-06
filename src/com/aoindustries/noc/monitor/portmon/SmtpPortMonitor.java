/*
 * Copyright 2001-2013, 2016, 2017 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.util.StringUtility;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Base64;

/**
 * Monitors with SMTP-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class SmtpPortMonitor extends DefaultTcpPortMonitor {

	private final HttpParameters monitoringParameters;

	public SmtpPortMonitor(InetAddress ipAddress, Port port, HttpParameters monitoringParameters) {
		super(ipAddress, port);
		this.monitoringParameters = monitoringParameters;
	}

	@Override
	public String checkPort(InputStream socketIn, OutputStream socketOut) throws Exception {
		// Get the configuration
		String from = StringUtility.nullIfEmpty(monitoringParameters.getParameter("from"));
		if(from == null) {
			throw new IllegalArgumentException("monitoringParameters does not include the from parameter");
		}
		String recipient = StringUtility.nullIfEmpty(monitoringParameters.getParameter("recipient"));
		if(recipient == null) {
			throw new IllegalArgumentException("monitoringParameters does not include the recipient parameter");
		}
		// Optional for authenticated SMTP
		String username = StringUtility.nullIfEmpty(monitoringParameters.getParameter("username"));
		String password = StringUtility.nullIfEmpty(monitoringParameters.getParameter("password"));
		if((username == null) != (password == null)) {
			throw new IllegalArgumentException("monitoringParameters must include either both username and password or neither");
		}
		// Null character used in AUTH PLAIN protocol below, make sure not in the username/password directly
		if(username != null && username.indexOf('\0') != -1) {
			throw new IllegalArgumentException("monitoringParameters contains illegal null in username");
		}
		if(password != null && password.indexOf('\0') != -1) {
			throw new IllegalArgumentException("monitoringParameters contains illegal null in password");
		}
		Charset charset = Charset.forName("US-ASCII");
		try (
			PrintWriter out = new PrintWriter(new OutputStreamWriter(socketOut, charset));
			BufferedReader in = new BufferedReader(new InputStreamReader(socketIn, charset))
		) {
			// Status line
			String line = in.readLine();
			if(line == null) throw new EOFException("End of file reading status");
			if(!line.startsWith("220 ")) throw new IOException("Unexpected status line: " + line);
			// NOTE: We are assuming ESMTP here
			// EHLO
			out.print("EHLO ");
			out.println(java.net.InetAddress.getLocalHost().getCanonicalHostName());
			out.flush();
			//List<String> ehloResponse = new ArrayList<>();
			while(true) {
				line = in.readLine();
				if(line == null) throw new EOFException("End of file reading EHLO response");
				if(line.startsWith("250-")) {
					// With continuation
					//ehloResponse.add(line.substring(4));
				} else if(line.startsWith("250 ")) {
					// End of response
					//ehloResponse.add(line.substring(4));
					break;
				} else {
					throw new IOException("Unexpected line reading EHLO response: " + line);
				}
			}
			if(username != null) {
				// NOTE: We are assuming AUTH PLAIN here
				// AUTH PLAIN
				out.print("AUTH PLAIN ");
				// See http://www.fehcom.de/qmail/smtpauth.html
				String authMessage = "\0" + username + "\0" + password;
				//out.println(Base64Coder.encode(authMessage.getBytes(charset)));
				out.println(Base64.getEncoder().encodeToString(authMessage.getBytes(charset)));
				out.flush();
				line = in.readLine();
				if(line == null) throw new EOFException("End of file reading AUTH PLAIN response");
				if(
					!line.startsWith("235 2.0.0 ")
					&& !line.startsWith("235 2.7.0 ")
				) throw new IOException("Unexpected line reading AUTH PLAIN response: " + line);
			}
			// MAIL From
			out.print("MAIL From:");
			out.println(from);
			out.flush();
			line = in.readLine();
			if(line == null) throw new EOFException("End of file reading MAIL From response");
			if(!line.startsWith("250 2.1.0 ")) throw new IOException("Unexpected line reading MAIL From response: " + line);
			// RCPT To
			out.print("RCPT To:");
			out.println(recipient);
			out.flush();
			line = in.readLine();
			if(line == null) throw new EOFException("End of file reading RCPT To response");
			if(!line.startsWith("250 2.1.5 ")) throw new IOException("Unexpected line reading RCPT To response: " + line);
			// DATA
			out.println("DATA");
			out.flush();
			line = in.readLine();
			if(line == null) throw new EOFException("End of file reading DATA response");
			if(!line.startsWith("354 ")) throw new IOException("Unexpected line reading DATA response: " + line);
			// Message headers and body
			out.print("To: ");
			out.println(recipient);
			out.print("From: ");
			out.println(from);
			out.println("Subject: SMTP monitoring message");
			out.println();
			out.println("This message is generated for SMTP port monitoring.");
			out.println(".");
			out.flush();
			line = in.readLine();
			if(line == null) throw new EOFException("End of file reading DATA response");
			if(!line.startsWith("250 2.0.0 ")) throw new IOException("Unexpected line reading DATA response: " + line);
			String result = line.substring(10);
			// Quit
			out.println("QUIT");
			out.flush();
			line = in.readLine();
			if(line == null) throw new EOFException("End of file reading QUIT response");
			if(!line.startsWith("221 2.0.0 ")) throw new IOException("Unexpected line reading QUIT response: " + line);
			// Return OK result
			return result;
		}
	}
}
