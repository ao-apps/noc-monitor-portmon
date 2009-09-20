package com.aoindustries.aoserv.portmon;
/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.*;
import com.aoindustries.aoserv.daemon.*;
import com.aoindustries.profiler.*;
import com.aoindustries.util.*;
import com.oreilly.servlet.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

/**
 * A <code>PortConnector</code> is the thread to actually perform
 * a connection to a port.  A pool of threads is maintained, but
 * new threads will always be created if the pool is all used.  Using
 * new threads avoids the problems related to <code>Thread.interrupt()</code>
 * interrupting a thread that is blocked on socket I/O.
 *
 * @author  AO Industries, Inc.
 */
public final class PortConnector extends Thread {

    static final boolean REPORT_EXCEPTIONS=true;

    private static final int MAX_THREADS=200;

    private static final int MAX_IDLE_THREADS=20;

    private static final int MAX_PORT_CONNECT_DELAY=60*1000;

    private static final int IDLE_RECOUNT_DELAY=5*60*1000;

    private static final List<PortConnector> threads=new ArrayList<PortConnector>();

    static void checkPort(
        AOServer aoServer,
        String ipAddress,
        int port,
        String netProtocol,
        String appProtocol
    ) {
        Profiler.startProfile(Profiler.UNKNOWN, PortConnector.class, "checkPort(AOServer,String,int,String,String)", null);
        try {
            // Look for an idle thread
            synchronized(threads) {
                int size=threads.size();
                for(int c=0;c<size;c++) {
                    PortConnector connector=threads.get(c);
                    if(connector.isReady()) {
                        connector.connect(
                            aoServer,
                            ipAddress,
                            port,
                            netProtocol,
                            appProtocol
                        );
                        return;
                    }
                }
                // No thread found, try to allocate a new one
                if(size>=MAX_THREADS) throw new RuntimeException("Maximum number of PortConnectors achieved: "+size);
                PortConnector connector=new PortConnector(
                    aoServer,
                    ipAddress,
                    port,
                    netProtocol,
                    appProtocol
                );
                threads.add(connector);
                connector.start();
            }
        } finally {
            Profiler.endProfile(Profiler.UNKNOWN);
        }
    }

    static void watchdog() throws IOException, SQLException {
        synchronized(threads) {
            int size=threads.size();
            for(int c=0;c<size;c++) {
                PortConnector connector=threads.get(c);
                long startTime;
                long endTime;
                boolean timedOut;
                synchronized(connector.stateLock) {
                    startTime=connector.startTime;
                    endTime=connector.endTime;
                    timedOut=connector.timedOut;
                }
                if(!timedOut && endTime==-1) {
                    long timeSince=System.currentTimeMillis()-startTime;
                    if(timeSince<0) {
                        synchronized(connector.stateLock) {
                            connector.startTime=System.currentTimeMillis();
                        }
                    } else if(timeSince>=MAX_PORT_CONNECT_DELAY) {
                        BatchEmailer.batchError(
                            BatchEmailer.TIME_OUT,
                            connector.aoServer,
                            connector.ipAddress,
                            connector.port,
                            connector.netProtocol,
                            connector.appProtocol
                        );
                        // Avoid other errors from the connector
                        synchronized(connector.stateLock) {
                            connector.timedOut=true;
                        }
                    }
                }
            }
        }
    }

    private final Object stateLock=new Object();
    private long startTime;
    private long endTime;
    private AOServer aoServer;
    private String ipAddress;
    private int port;
    private String netProtocol;
    private String appProtocol;
    private boolean timedOut;

    private PortConnector(
        AOServer aoServer,
        String ipAddress,
        int port,
        String netProtocol,
        String appProtocol
    ) {
        Profiler.startProfile(Profiler.FAST, PortConnector.class, "<init>(AOServer,String,int,String,String)", null);
        try {
            this.startTime=System.currentTimeMillis();
            this.endTime=-1;
            this.aoServer=aoServer;
            this.ipAddress=ipAddress;
            this.port=port;
            this.netProtocol=netProtocol;
            this.appProtocol=appProtocol;
            this.timedOut=false;
        } finally {
            Profiler.endProfile(Profiler.FAST);
        }
    }
    
    private boolean isReady() {
        Profiler.startProfile(Profiler.FAST, PortConnector.class, "isReady()", null);
        try {
            synchronized(stateLock) {
                return endTime!=-1;
            }
        } finally {
            Profiler.endProfile(Profiler.FAST);
        }
    }
    
    private final Object waitLock=new Object();
    private void connect(
        AOServer aoServer,
        String ipAddress,
        int port,
        String netProtocol,
        String appProtocol
    ) {
        Profiler.startProfile(Profiler.UNKNOWN, PortConnector.class, "connect(AOServer,String,int,String,String)", null);
        try {
            synchronized(waitLock) {
                this.startTime=System.currentTimeMillis();
                this.endTime=-1;
                this.aoServer=aoServer;
                this.ipAddress=ipAddress;
                this.port=port;
                this.netProtocol=netProtocol;
                this.appProtocol=appProtocol;
                this.timedOut=false;
                waitLock.notify();
            }
        } finally {
            Profiler.endProfile(Profiler.UNKNOWN);
        }
    }

    public void run() {
        Profiler.startProfile(Profiler.UNKNOWN, PortConnector.class, "run()", null);
        try {
            synchronized(waitLock) {
                while(true) {
                    try {
                        Socket socket=null;
                        DatagramSocket datagramSocket=null;
                        try {
                            try {
                                // Check one port
                                if (
                                    appProtocol.equals(Protocol.IMAP2)
                                    || appProtocol.equals(Protocol.POP3)
                                    || appProtocol.equals(Protocol.FTP)
                                ) {
                                    String username, password;
                                    if(appProtocol.equals(Protocol.IMAP2) || appProtocol.equals(Protocol.POP3)) {
                                        username=LinuxAccount.EMAILMON;
                                        password=aoServer.getEmailmonPassword();
                                    } else if(appProtocol.equals(Protocol.FTP)) {
                                        username=LinuxAccount.FTPMON;
                                        password=aoServer.getFtpmonPassword();
                                    } else throw new RuntimeException("Unexpected appProtocol: "+appProtocol);

                                    if(password==null) {
                                        try {
                                            BatchEmailer.batchError(BatchEmailer.IO_EXCEPTION, aoServer, ipAddress, port, netProtocol, appProtocol);
                                        } catch(SQLException err) {
                                            AOServDaemon.reportError(
                                                err,
                                                new Object[] {
                                                    "aoServer="+aoServer,
                                                    "ipAddress="+ipAddress,
                                                    "port="+port,
                                                    "netProtocol="+netProtocol,
                                                    "appProtocol="+appProtocol
                                                }
                                            );
                                        }
                                    } else {
                                        socket = new Socket();
                                        socket.setKeepAlive(true);
                                        socket.setSoLinger(true, AOPool.DEFAULT_SOCKET_SO_LINGER);
                                        //socket.setTcpNoDelay(true);
                                        socket.setSoTimeout(60000);
                                        socket.connect(new InetSocketAddress(ipAddress, port), 10*1000);
                                        PrintWriter out = new PrintWriter(socket.getOutputStream());
                                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                                        boolean protocolError;
                                        if (appProtocol.equals(Protocol.IMAP2)) {
                                            String line = in.readLine();
                                            if (line!=null && line.length()>=16 && line.substring(0,16).equals("* OK [CAPABILITY")) {
                                                out.println("AA LOGIN "+username+" \""+password+"\"");
                                                out.flush();
                                                line = in.readLine();
                                                if (line!=null && line.length()>=17 && line.substring(0,17).equals("AA OK [CAPABILITY")) {
                                                    out.println("AB LOGOUT");
                                                    out.flush();
                                                    line = in.readLine();
                                                    if (line!=null && line.length()>=5 && line.substring(0,5).equals("* BYE")) {
                                                        line = in.readLine();
                                                        if (line!=null && line.length()>=12 && line.substring(0,12).equals("AB OK LOGOUT")) {
                                                            protocolError=false;
                                                        } else protocolError=true;
                                                    } else protocolError=true;
                                                } else protocolError=true;
                                            } else protocolError=true;
                                        } else if(appProtocol.equals(Protocol.POP3)) {
                                            String line = in.readLine();
                                            if (line!=null && line.length()>=8 && line.substring(0,8).equals("+OK POP3")) {
                                                out.println("user "+username);
                                                out.flush();
                                                line = in.readLine();
                                                if (line!=null && line.equals("+OK User name accepted, password please")) {
                                                    out.println("pass "+password);
                                                    out.flush();
                                                    line = in.readLine();
                                                    if (line!=null && line.length()>=16 && line.substring(0,16).equals("+OK Mailbox open")) {
                                                        out.println("quit");
                                                        out.flush();
                                                        line = in.readLine();
                                                        if (line!=null && line.length()>=12 && line.substring(0,12).equals("+OK Sayonara")) {
                                                            protocolError=false;
                                                        } else protocolError=true;
                                                    } else protocolError=true;
                                                } else protocolError=true;
                                            } else protocolError=true;
                                        } else if(appProtocol.equals(Protocol.FTP)) {
                                            String line = in.readLine();
                                            if (line!=null && line.length()>=3 && line.substring(0,3).equals("220")) {
                                                out.println("user "+username);
                                                out.flush();
                                                line = in.readLine();
                                                if (line!=null && line.length()>=3 && line.substring(0,3).equals("331")) {
                                                    out.println("pass "+password);
                                                    out.flush();
                                                    line = in.readLine();
                                                    if (line!=null && line.length()>=3 && line.substring(0,3).equals("230")) {
                                                        out.println("quit");
                                                        out.flush();
                                                        line = in.readLine();
                                                        if (line!=null && line.length()>=3 && line.substring(0,3).equals("221")) {
                                                            protocolError=false;
                                                            while ((line = in.readLine())!=null) {
                                                                if (line.length()<3 || !line.substring(0,3).equals("221")) {
                                                                    protocolError = true;
                                                                    break;
                                                                }
                                                            }
                                                        } else protocolError=true;
                                                    } else protocolError=true;
                                                } else protocolError=true;
                                            } else protocolError=true;
                                        } else throw new RuntimeException("Unknown appProtocol: "+appProtocol);

                                        if (protocolError) {
                                            if(!timedOut) {
                                                try {
                                                    BatchEmailer.batchError(
                                                        BatchEmailer.PROTOCOL_ERROR,
                                                        aoServer,
                                                        ipAddress,
                                                        port,
                                                        netProtocol,
                                                        appProtocol
                                                    );
                                                } catch(SQLException err2) {
                                                    AOServDaemon.reportError(
                                                        err2,
                                                        new Object[] {
                                                            "aoServer="+aoServer,
                                                            "ipAddress="+ipAddress,
                                                            "port="+port,
                                                            "netProtocol="+netProtocol,
                                                            "appProtocol="+appProtocol
                                                        }
                                                    );
                                                }
                                            }
                                        }
                                    }
                                } else if (appProtocol.equals(Protocol.SMTP)) {
                                    try {
                                        String from="portmon@aoindustries.com";
                                        String to="devnull@"+aoServer.getServer().getHostname();
                                        MailMessage msg = new MailMessage(ipAddress);
                                        msg.from(from);
                                        msg.to(to);
                                        msg.setSubject("SMTP monitoring message");
                                        PrintStream email = msg.getPrintStream();
                                        email.print("This message is generated for SMTP port monitoring.");
                                        msg.sendAndClose();
                                    } catch(RuntimeException err2) {
                                        AOServDaemon.reportError(
                                            err2,
                                            new Object[] {
                                                "aoServer="+aoServer
                                            }
                                        );
                                    }
                                } else if (
                                    appProtocol.equals(Protocol.AOSERV_MASTER_SSL)
                                    || appProtocol.equals(Protocol.AOSERV_MASTER)
                                ) {
                                    AOServDaemon.getConnector().ping();
                                } else {
                                    if (netProtocol.equals(NetProtocol.TCP)) {
                                        socket=new Socket();
                                        socket.setKeepAlive(true);
                                        socket.setSoLinger(false, AOPool.DEFAULT_SOCKET_SO_LINGER);
                                        //socket.setTcpNoDelay(true);
                                        socket.setSoTimeout(60000);
                                        socket.connect(new InetSocketAddress(ipAddress, port), 10*1000);
                                    } else if (netProtocol.equals(NetProtocol.UDP)) {
                                        datagramSocket=new DatagramSocket();
                                        datagramSocket.connect(InetAddress.getByName(ipAddress), port);
                                    } else if(!netProtocol.equals(NetProtocol.RAW)) {
                                        throw new RuntimeException("Unknown netProtocol: "+netProtocol);
                                    }
                                }
                            } finally {
                                try {
                                    if (socket!=null) {
                                        socket.close();
                                        socket=null;
                                    }
                                    if(datagramSocket!=null) {
                                        datagramSocket.disconnect();
                                        datagramSocket.close();
                                        datagramSocket=null;
                                    }
                                } catch(IOException err) {
                                    if(REPORT_EXCEPTIONS) {
                                        AOServDaemon.reportError(err, null);
                                    }
                                    if(!timedOut) {
                                        try {
                                            BatchEmailer.batchError(BatchEmailer.IO_EXCEPTION, aoServer, ipAddress, port, netProtocol, appProtocol);
                                        } catch(SQLException err2) {
                                            AOServDaemon.reportError(
                                                err2,
                                                new Object[] {
                                                    "aoServer="+aoServer,
                                                    "ipAddress="+ipAddress,
                                                    "port="+port,
                                                    "netProtocol="+netProtocol,
                                                    "appProtocol="+appProtocol
                                                }
                                            );
                                        }
                                    }
                                }
                            }
                        } catch(ConnectException err) {
                            if(REPORT_EXCEPTIONS) {
                                AOServDaemon.reportError(err, null);
                            }
                            if(!timedOut) {
                                try {
                                    BatchEmailer.batchError(BatchEmailer.CONNECT_EXCEPTION, aoServer, ipAddress, port, netProtocol, appProtocol);
                                } catch(IOException err2) {
                                    AOServDaemon.reportError(
                                        err2,
                                        new Object[] {
                                            "aoServer="+aoServer,
                                            "ipAddress="+ipAddress,
                                            "port="+port,
                                            "netProtocol="+netProtocol,
                                            "appProtocol="+appProtocol
                                        }
                                    );
                                } catch(SQLException err2) {
                                    AOServDaemon.reportError(
                                        err2,
                                        new Object[] {
                                            "aoServer="+aoServer,
                                            "ipAddress="+ipAddress,
                                            "port="+port,
                                            "netProtocol="+netProtocol,
                                            "appProtocol="+appProtocol
                                        }
                                    );
                                }
                            }
                        } catch(IOException err) {
                            if(REPORT_EXCEPTIONS) {
                                AOServDaemon.reportError(err, null);
                            }
                            if(!timedOut) {
                                try {
                                    BatchEmailer.batchError(BatchEmailer.IO_EXCEPTION, aoServer, ipAddress, port, netProtocol, appProtocol);
                                } catch(IOException err2) {
                                    AOServDaemon.reportError(
                                        err2,
                                        new Object[] {
                                            "aoServer="+aoServer,
                                            "ipAddress="+ipAddress,
                                            "port="+port,
                                            "netProtocol="+netProtocol,
                                            "appProtocol="+appProtocol
                                        }
                                    );
                                } catch(SQLException err2) {
                                    AOServDaemon.reportError(
                                        err2,
                                        new Object[] {
                                            "aoServer="+aoServer,
                                            "ipAddress="+ipAddress,
                                            "port="+port,
                                            "netProtocol="+netProtocol,
                                            "appProtocol="+appProtocol
                                        }
                                    );
                                }
                            }
                        } finally {
                            try {
                                if(socket!=null) {
                                    socket.close();
                                }
                                if(datagramSocket!=null) {
                                    datagramSocket.disconnect();
                                    datagramSocket.close();
                                }
                            } catch(IOException err) {
                                if(REPORT_EXCEPTIONS) {
                                    AOServDaemon.reportError(err, null);
                                }
                                if(!timedOut) {
                                    try {
                                        BatchEmailer.batchError(BatchEmailer.IO_EXCEPTION, aoServer, ipAddress, port, netProtocol, appProtocol);
                                    } catch(IOException err2) {
                                        AOServDaemon.reportError(
                                            err2,
                                            new Object[] {
                                                "aoServer="+aoServer,
                                                "ipAddress="+ipAddress,
                                                "port="+port,
                                                "netProtocol="+netProtocol,
                                                "appProtocol="+appProtocol
                                            }
                                        );
                                    } catch(SQLException err2) {
                                        AOServDaemon.reportError(
                                            err2,
                                            new Object[] {
                                                "aoServer="+aoServer,
                                                "ipAddress="+ipAddress,
                                                "port="+port,
                                                "netProtocol="+netProtocol,
                                                "appProtocol="+appProtocol
                                            }
                                        );
                                    }
                                }
                            }
                        }
                    } finally {
                        synchronized(stateLock) {
                            this.endTime=System.currentTimeMillis();
                        }
                    }

                    // Wait until there are too many free threads or a new port has been assigned
                    while(true) {
                        synchronized(threads) {
                            int count=0;
                            int size=threads.size();
                            for(int c=0;c<size;c++) {
                                PortConnector connector=threads.get(c);
                                if(connector.isReady()) {
                                    count++;
                                    if(count>=MAX_IDLE_THREADS) {
                                        threads.remove(this);
                                        return;
                                    }
                                }
                            }
                        }
                        try {
                            waitLock.wait(IDLE_RECOUNT_DELAY);
                        } catch(InterruptedException err) {
                            // Normal
                        }
                        long endTime;
                        synchronized(stateLock) {
                            endTime=this.endTime;
                        }
                        if(endTime==-1) {
                            break;
                        }
                    }
                }
            }
        } finally {
            Profiler.endProfile(Profiler.UNKNOWN);
        }
    }
}