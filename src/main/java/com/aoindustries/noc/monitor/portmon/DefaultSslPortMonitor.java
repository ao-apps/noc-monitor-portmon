/*
 * noc-monitor-portmon - Port monitoring implementations.
 * Copyright (C) 2018, 2019, 2020  AO Industries, Inc.
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
 * along with noc-monitor-portmon.  If not, see <http://www.gnu.org/licenses/>.
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
