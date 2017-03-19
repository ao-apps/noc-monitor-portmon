/*
 * Copyright 2001-2013, 2016, 2017 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

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

	@Override
	public String checkPort(InputStream socketIn, OutputStream socketOut) throws Exception {
		// Get the configuration
		String username = monitoringParameters.getParameter("username");
		if(username==null || username.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the username");
		String password = monitoringParameters.getParameter("password");
		if(password==null || password.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the password");

		Charset charset = Charset.forName("US-ASCII");
		try (
			PrintWriter out = new PrintWriter(new OutputStreamWriter(socketOut, charset));
			BufferedReader in = new BufferedReader(new InputStreamReader(socketIn, charset))
		) {
			// Capabilities
			String line = in.readLine();
			if(line==null) throw new EOFException("End of file reading capabilities");
			if(!line.startsWith("* OK [CAPABILITY")) throw new IOException("Unexpected capabilities line: "+line);
			// Login
			out.println("AA LOGIN "+username+" \""+password+"\"");
			out.flush();
			line = in.readLine();
			if(line==null) throw new EOFException("End of file reading login response");
			int bracketPos = line.indexOf(']');
			if(!line.startsWith("AA OK [CAPABILITY") || bracketPos==-1) throw new IOException("Unexpected line reading login response: "+line);
			String result = line.substring(bracketPos+1).trim();
			// Logout
			out.println("AB LOGOUT");
			out.flush();
			line = in.readLine();
			if(line==null) throw new EOFException("End of file reading logout response line 1");
			if(!line.startsWith("* BYE") && !line.startsWith("AB OK LOGOUT")) throw new IOException("Unexpected line reading logout response line 1: "+line);
			//if(line==null) throw new EOFException("End of file reading logout response line 2");
			//if(!line.startsWith("* BYE") && !line.startsWith("AB OK LOGOUT")) throw new IOException("Unexpected line reading logout response line 2: "+line);
			// Return OK result
			return result;
		}
	}
}
