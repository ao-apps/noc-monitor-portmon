/*
 * Copyright 2001-2013, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.portmon;

import com.aoindustries.aoserv.client.Protocol;
import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import java.io.IOException;
import java.io.Reader;

/**
 * A <code>PortMonitor</code> connects to a service on a port and verifies it is
 * working correctly.  The monitor will only be used at most once per instance.
 *
 * TODO: Support chronyd protocol: chronyc tracking
 *
 * @author  AO Industries, Inc.
 */
public abstract class PortMonitor {

	/**
	 * Factory method to get the best port monitor for the provided port
	 * details.  If can't find any monitor, will through IllegalArgumentException.
	 */
	public static PortMonitor getPortMonitor(InetAddress ipAddress, Port port, String appProtocol, HttpParameters monitoringParameters) throws IllegalArgumentException {
		com.aoindustries.net.Protocol netProtocol = port.getProtocol();
		switch (netProtocol) {
			case UDP:
				// UDP
				return new DefaultUdpPortMonitor(ipAddress, port);
			case TCP:
				// TCP
				if(Protocol.FTP.equals(appProtocol)) return new FtpPortMonitor(ipAddress, port, monitoringParameters);
				// TODO: HTTP(S) protocol support, with application-defined criteria
				if(Protocol.HTTPS.equals(appProtocol)) return new DefaultSslPortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.IMAP2.equals(appProtocol)) return new ImapPortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.SIMAP.equals(appProtocol)) return new SImapPortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.MYSQL.equals(appProtocol)) return new MySQLPortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.POP3.equals(appProtocol)) return new Pop3PortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.SPOP3.equals(appProtocol)) return new SPop3PortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.POSTGRESQL.equals(appProtocol) && !ipAddress.isLoopback()) return new PostgresSQLPortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.SMTP.equals(appProtocol) || Protocol.SUBMISSION.equals(appProtocol)) return new SmtpPortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.SMTPS.equals(appProtocol)) return new SmtpsPortMonitor(ipAddress, port, monitoringParameters);
				if(Protocol.SSH.equals(appProtocol)) return new SshPortMonitor(ipAddress, port);
				return new DefaultTcpPortMonitor(ipAddress, port);
			default:
				throw new IllegalArgumentException("Unable to find port monitor: ipAddress=\""+ipAddress+"\", port="+port+", appProtocol=\""+appProtocol+"\"");
		}
	}

	protected static String readLine(Reader in, StringBuilder buffer) throws IOException {
		buffer.setLength(0);
		while(true) {
			int ch = in.read();
			if(ch == -1) {
				if(buffer.length() == 0) return null;
				break;
			}
			if(ch == '\n') break;
			if(ch != '\r') buffer.append((char)ch);
		}
		String s = buffer.toString();
		buffer.setLength(0);
		return s;
	}

	protected static final String CRLF = "\r\n";

	protected final InetAddress ipAddress;
	protected final Port port;
	protected volatile boolean canceled;

	protected PortMonitor(InetAddress ipAddress, Port port) {
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
