package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.io.AOPool;
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

    private volatile Socket socket;

    public DefaultTcpPortMonitor(String ipAddress, int port) {
        super(ipAddress, port);
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

    @Override
    final public String checkPort() throws Exception {
        socket=new Socket();
        try {
            socket.setKeepAlive(true);
            socket.setSoLinger(true, AOPool.DEFAULT_SOCKET_SO_LINGER);
            //socket.setTcpNoDelay(true);
            socket.setSoTimeout(60000);
            socket.connect(new InetSocketAddress(ipAddress, port), 60*1000);

            return checkPort(socket.getInputStream(), socket.getOutputStream());
        } finally {
            socket.close();
        }
    }

    /**
     * Performs any protocol-specific monitoring.  This default implementation does
     * nothing.
     */
    protected String checkPort(InputStream in, OutputStream out) throws Exception {
        return "Connected successfully";
    }
}
