/*
 * Copyright 2001-2013, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.io.AOPool;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.net.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors any TCP port by simply connecting and disconnecting.  Additional
 * protocol-specific checks are performed by subclasses overriding the checkPort
 * method.
 *
 * @author  AO Industries, Inc.
 */
public class DefaultTcpPortMonitor extends PortMonitor {

	private static final Logger logger = Logger.getLogger(DefaultTcpPortMonitor.class.getName());

	protected static final int TIMEOUT = 60_000;

	private volatile Socket socket;

	public DefaultTcpPortMonitor(InetAddress ipAddress, Port port) {
		super(ipAddress, port);
		if(port.getProtocol() != Protocol.TCP) throw new IllegalArgumentException("port not TCP: " + port);
	}

	@Override
	public void cancel() {
		super.cancel();
		Socket mySocket = socket;
		if(mySocket!=null) {
			try {
				mySocket.close();
			} catch(IOException err) {
				logger.log(Level.WARNING, null, err);
			}
		}
	}

	/**
	 * Gets the socket to use for this port connection.
	 */
	protected Socket connect() throws Exception {
		boolean successful = false;
		Socket s = new Socket();
		try {
			s.setKeepAlive(true);
			s.setSoLinger(true, AOPool.DEFAULT_SOCKET_SO_LINGER);
			//s.setTcpNoDelay(true);
			s.setSoTimeout(TIMEOUT);
			s.connect(new InetSocketAddress(ipAddress.toString(), port.getPort()), TIMEOUT);
			successful = true;
			return s;
		} finally {
			if(!successful) s.close();
		}
	}

	@Override
	final public String checkPort() throws Exception {
		socket = connect();
		try {
			return checkPort(socket, socket.getInputStream(), socket.getOutputStream());
		} finally {
			socket.close();
		}
	}

	protected static final String CONNECTED_SUCCESSFULLY = "Connected successfully";

	/**
	 * Performs any protocol-specific monitoring.  This default implementation does
	 * nothing.
	 */
	protected String checkPort(Socket socket, InputStream in, OutputStream out) throws Exception {
		return CONNECTED_SUCCESSFULLY;
	}
}
