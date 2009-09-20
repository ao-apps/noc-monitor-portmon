package com.aoindustries.aoserv.portmon;
/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.*;
import com.aoindustries.aoserv.daemon.*;
import com.aoindustries.profiler.*;
import java.util.*;

/**
 * The <code>PortMonitor</code> assigns ports to the allocated
 * <code>PortConnector</code>s.  It also starts a <code>Watchdog</code>
 * that notifies when a port has not been distributed in enough time or
 * a <code>PortConnector</code> takes too long to connect.
 *
 * @author  AO Industries, Inc.
 */
public final class PortMonitor implements Runnable {

    /**
     * The time between scanning each port.
     */
    private static final int SCAN_DELAY=500;
    
    /**
     * The time between each scan pass.
     */
    private static final int SCAN_INTERVAL=5*60*1000;
    
    private static Thread thread=null;
    static Watchdog watchdog=null;
    static BatchEmailer batchEmailer=null;

    public static void main(String[] args) {
        Profiler.startProfile(Profiler.INSTANTANEOUS, PortMonitor.class, "main(String[])", null);
        try {
            start();
        } finally {
            Profiler.endProfile(Profiler.INSTANTANEOUS);
        }
    }

    public static void start() {
        Profiler.startProfile(Profiler.UNKNOWN, PortMonitor.class, "start()", null);
        try {
            if(thread==null) {
                synchronized(System.out) {
                    if(thread==null) {
                        System.out.print("Starting PortMonitor: ");
                        (thread=new Thread(new PortMonitor(), "PortMonitor")).start();
                        (watchdog=new Watchdog()).start();
                        (batchEmailer=new BatchEmailer()).start();
                        System.out.println("Done");
                    }
                }
            }
        } finally {
            Profiler.endProfile(Profiler.UNKNOWN);
        }
    }

    public void run() {
        Profiler.startProfile(Profiler.UNKNOWN, PortMonitor.class, "run()", null);
        try {
            while (true) {
                try {
                    // Values used inside the loop
                    Random random=AOServDaemon.getRandom();
                    List<IPAddress> pubs=new ArrayList<IPAddress>();
                    AOServConnector conn=AOServDaemon.getConnector();

                    while (true) {
                        long startTime=System.currentTimeMillis();
                        List<NetBind> binds = new ArrayList<NetBind>();
                        binds.addAll(conn.netBinds.values());
                        Collections.shuffle(binds);
                        for (int i=0; i<binds.size(); i++) {
                            NetBind bind = binds.get(i);
                            if (bind.isFirewallOpen() && bind.isMonitoringEnabled()) {
                                AOServer aoServer=bind.getAOServer();
                                if(aoServer.isMonitoringEnabled()) {
                                    IPAddress bindAddress = bind.getIPAddress();
                                    String ipString=bindAddress.getIPAddress();
                                    if (!ipString.equals(IPAddress.LOOPBACK_IP)) {
                                        if (ipString.equals(IPAddress.WILDCARD_IP)) {
                                            // Only check the main IP addresses for wildcard
                                            List<IPAddress> ipAddresses = aoServer.getIPAddresses();

                                            // Find a random, non-primary IP Address
                                            pubs.clear();
                                            for(int c=0;c<ipAddresses.size();c++) {
                                                IPAddress ia=ipAddresses.get(c);
                                                if(
                                                    ia.getNetDevice()!=null
                                                    && !ia.isPrivate()
                                                ) pubs.add(ia);
                                            }
                                            IPAddress randomAddress=pubs.get(random.nextInt(pubs.size()));

                                            // Connect to the random IP
                                            PortConnector.checkPort(
                                                aoServer,
                                                randomAddress.getIPAddress(),
                                                bind.getPort().getPort(),
                                                bind.getNetProtocol().getProtocol(),
                                                bind.getAppProtocol().getProtocol()
                                            );
                                        } else {
                                            if (
                                                !bindAddress.isPrivate()
                                            ) {
                                                PortConnector.checkPort(
                                                    bind.getAOServer(),
                                                    ipString,
                                                    bind.getPort().getPort(),
                                                    bind.getNetProtocol().getProtocol(),
                                                    bind.getAppProtocol().getProtocol()
                                                );
                                            }
                                        }
                                    }

                                    // Let the watchdog know we are OK
                                    watchdog.portDistributed();

                                    // Wait for the next run
                                    try {
                                        Thread.sleep(SCAN_DELAY);
                                    } catch(InterruptedException err) {
                                        AOServDaemon.reportWarning(err, null);
                                    }
                                }
                            }
                        }
                        long scanTime=System.currentTimeMillis()-startTime;
                        if(scanTime>=0 && scanTime<SCAN_INTERVAL) {
                            try {
                                Thread.sleep(SCAN_INTERVAL-scanTime);
                            } catch(InterruptedException err) {
                                AOServDaemon.reportWarning(err, null);
                            }
                        }
                    }
                } catch (ThreadDeath err) {
                    throw err;
                } catch (Throwable err) {
                    AOServDaemon.reportError(err, null);
                    try {
                        Thread.currentThread().sleep(60000);
                    } catch (InterruptedException errIE) {
                        AOServDaemon.reportWarning(errIE, null);
                    }
                }
            }
        } finally {
            Profiler.endProfile(Profiler.UNKNOWN);
        }
    }
}