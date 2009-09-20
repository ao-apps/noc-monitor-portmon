package com.aoindustries.aoserv.portmon;
/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.daemon.*;
import com.aoindustries.profiler.*;

/**
 * The <code>Watchdog</code> makes sure that the <code>PortMonitor</code>
 * distributes its ports in a timely manner.  Also makes sure that
 * each of the <code>PortConnector</code>s are able to connect quickly
 * enough.
 *
 * @author  AO Industries, Inc.
 */
public final class Watchdog extends Thread {

    private static final int SCAN_INTERVAL=15*1000;
    
    /**
     * Is on a five minute interval, so allow an additional 5 minute delay.
     */
    private static final int MAX_PORT_DISTRIBUTION_DELAY=10*60*1000;
    
    private Object lastPortDistributedLock=new Object();
    private long lastPortDistributedTime=-1;
    void portDistributed() {
        Profiler.startProfile(Profiler.FAST, Watchdog.class, "portDistributed()", null);
        try {
            synchronized(lastPortDistributedLock) {
                this.lastPortDistributedTime=System.currentTimeMillis();
            }
        } finally {
            Profiler.endProfile(Profiler.FAST);
        }
    }

    Watchdog() {
        super("Watchdog");
        Profiler.startProfile(Profiler.FAST, Watchdog.class, "<init>()", null);
        try {
            setPriority(Thread.NORM_PRIORITY+2);
        } finally {
            Profiler.endProfile(Profiler.FAST);
        }
    }
    
    public void run() {
        Profiler.startProfile(Profiler.UNKNOWN, Watchdog.class, "run()", null);
        try {
            while (true) {
                try {
                    while (true) {
                        long startTime=System.currentTimeMillis();
                        
                        // Check the port distribution
                        long lastTime;
                        synchronized(lastPortDistributedLock) {
                            lastTime=lastPortDistributedTime;
                        }
                        if(lastTime!=-1) {
                            long timeSinceDistribution=System.currentTimeMillis()-lastTime;

                            if(timeSinceDistribution<0) portDistributed();
                            else if(timeSinceDistribution>=MAX_PORT_DISTRIBUTION_DELAY) BatchEmailer.batchError(BatchEmailer.PORT_DISTRIBUTION);
                        } else {
                            synchronized(lastPortDistributedLock) {
                                lastPortDistributedTime=System.currentTimeMillis();
                            }
                        }

                        // Check for PortConnectors that are too delayed
                        PortConnector.watchdog();

                        long scanTime=System.currentTimeMillis()-startTime;
                        if(scanTime>0 && scanTime<=SCAN_INTERVAL) {
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