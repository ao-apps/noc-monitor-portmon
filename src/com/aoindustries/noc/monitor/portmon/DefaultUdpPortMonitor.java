package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Monitors any UDP port.
 *
 * @author  AO Industries, Inc.
 */
public class DefaultUdpPortMonitor extends PortMonitor {

    private volatile DatagramSocket datagramSocket;

    public DefaultUdpPortMonitor(String ipAddress, int port) {
        super(ipAddress, port);
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
            datagramSocket.connect(InetAddress.getByName(ipAddress), port);
        } finally {
            // datagramSocket.disconnect();
            datagramSocket.close();
        }
        return "Connected successfully";
    }
}
