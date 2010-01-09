package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.NetProtocol;
import com.aoindustries.aoserv.client.Protocol;
import com.aoindustries.aoserv.client.validator.InetAddress;
import com.aoindustries.aoserv.client.validator.NetPort;
import java.util.Map;

/**
 * A <code>PortMonitor</code> connects to a service on a port and verifies it is
 * working correctly.  The monitor will only be used at most once per instance.
 *
 * @author  AO Industries, Inc.
 */
public abstract class PortMonitor {

    /**
     * Factory method to get the best port monitor for the provided port
     * details.  If can't find any monitor, will through IllegalArgumentException.
     */
    public static PortMonitor getPortMonitor(InetAddress ipAddress, NetPort port, String netProtocol, String appProtocol, Map<String,String> monitoringParameters) throws IllegalArgumentException {
        if(NetProtocol.UDP.equals(netProtocol)) {
            // UDP
            return new DefaultUdpPortMonitor(ipAddress, port);
        } else if(NetProtocol.TCP.equals(netProtocol)) {
            // TCP
            if(Protocol.FTP.equals(appProtocol)) return new FtpPortMonitor(ipAddress, port, monitoringParameters);
            if(Protocol.IMAP2.equals(appProtocol)) return new ImapPortMonitor(ipAddress, port, monitoringParameters);
            if(Protocol.MYSQL.equals(appProtocol)) return new MySQLPortMonitor(ipAddress, port, monitoringParameters);
            if(Protocol.POP3.equals(appProtocol)) return new Pop3PortMonitor(ipAddress, port, monitoringParameters);
            if(Protocol.POSTGRESQL.equals(appProtocol) && !ipAddress.isLooback()) return new PostgresSQLPortMonitor(ipAddress, port, monitoringParameters);
            if(Protocol.SMTP.equals(appProtocol) || Protocol.SUBMISSION.equals(appProtocol)) return new SmtpPortMonitor(ipAddress, port, monitoringParameters);
            if(Protocol.SSH.equals(appProtocol)) return new SshPortMonitor(ipAddress, port);
            return new DefaultTcpPortMonitor(ipAddress, port);
        } else {
            throw new IllegalArgumentException("Unable to find port monitor: ipAddress=\""+ipAddress+"\", port="+port+", netProtocol=\""+netProtocol+"\", appProtocol=\""+appProtocol+"\"");
        }
    }

    protected final InetAddress ipAddress;
    protected final NetPort port;
    protected volatile boolean canceled;

    protected PortMonitor(InetAddress ipAddress, NetPort port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    /**
     * <p>
     * Cancels this port method on a best effort basis.  This will not necessarily cause the checkPort
     * method to return immediately.  This should only be used once the result
     * of checkPort is no longer relevant, such as after a timeout.  Some monitors
     * may still perform their task arbitrarily long after cancel has been called.
     * </p>
     * <p>
     * It is critical that subclass implementations of this method not block in any way.
     * </p>
     *
     * @see  #checkPort()
     */
    public void cancel() {
        canceled = true;
    }

    /**
     * Checks the port.  This may take arbitrarily long to complete, and any timeout
     * should be provided externally and call the <code>cancel</code> method.
     * If any error occurs, must throw an exception.
     *
     * @see  #cancel()
     *
     * @return  the message indicating success
     */
    public abstract String checkPort() throws Exception;
}
