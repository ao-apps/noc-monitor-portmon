/*
 * Copyright 2001-2009, 2016, 2017 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.net.Port;
import com.aoindustries.net.Protocol;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Monitors any UDP port.
 *
 * @author  AO Industries, Inc.
 */
public class DefaultUdpPortMonitor extends PortMonitor {

	private volatile DatagramSocket datagramSocket;

	public DefaultUdpPortMonitor(com.aoindustries.net.InetAddress ipAddress, Port port) {
		super(ipAddress, port);
		if(port.getProtocol() != Protocol.UDP) throw new IllegalArgumentException("port not UDP: " + port);
	}

	@Override
	public void cancel() {
		super.cancel();
		DatagramSocket myDatagramSocket = datagramSocket;
		if(myDatagramSocket!=null) myDatagramSocket.close();
	}

	@Override
	public String checkPort() throws Exception {
		datagramSocket=new DatagramSocket();
		try {
			datagramSocket.connect(InetAddress.getByName(ipAddress.toString()), port.getPort());
		} finally {
			// datagramSocket.disconnect();
			datagramSocket.close();
		}
		return "Connected successfully";
	}
}
