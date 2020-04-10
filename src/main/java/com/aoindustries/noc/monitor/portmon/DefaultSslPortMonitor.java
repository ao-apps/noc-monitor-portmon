/*
 * Copyright 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.net.URIParameters;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Monitors any SSL port by simply connecting and disconnecting.
 *
 * @author  AO Industries, Inc.
 */
public class DefaultSslPortMonitor extends DefaultTcpPortMonitor {

	private final boolean ssl;

	public DefaultSslPortMonitor(InetAddress ipAddress, Port port, URIParameters monitoringParameters) {
		super(ipAddress, port);
		// Use SSL unless explicitely disabled with ssl=false
		ssl = !"false".equalsIgnoreCase(monitoringParameters.getParameter("ssl"));
	}

	@Override
	protected Socket connect() throws Exception {
		boolean successful = false;
		Socket s = super.connect();
		try {
			if(ssl) {
				SSLSocketFactory sslFact = (SSLSocketFactory)SSLSocketFactory.getDefault();
				s = sslFact.createSocket(s, ipAddress.toString(), port.getPort(), true);
			}
			successful = true;
			return s;
		} finally {
			if(!successful) s.close();
		}
	}

	@Override
	public String checkPort(Socket socket, InputStream socketIn, OutputStream socketOut) throws Exception {
		if(ssl) {
			return CONNECTED_SUCCESSFULLY + " over SSL";
		} else {
			return CONNECTED_SUCCESSFULLY + " (SSL disabled)";
		}
	}
}
