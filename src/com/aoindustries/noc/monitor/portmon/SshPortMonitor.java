/*
 * Copyright 2001-2013, 2016, 2017 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

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
 * Monitors with SSH-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class SshPortMonitor extends DefaultTcpPortMonitor {

	public SshPortMonitor(InetAddress ipAddress, Port port) {
		super(ipAddress, port);
	}

	@Override
	public String checkPort(InputStream socketIn, OutputStream socketOut) throws Exception {
		Charset charset = Charset.forName("US-ASCII");
		try (
			PrintWriter out = new PrintWriter(new OutputStreamWriter(socketOut, charset));
			BufferedReader in = new BufferedReader(new InputStreamReader(socketIn, charset))
		) {
			// Status line
			String line = in.readLine();
			if(line==null) throw new EOFException("End of file reading status");
			if(!line.startsWith("SSH-")) throw new IOException("Unexpected status line: "+line);
			// Return OK result
			return line;
		}
	}
}
