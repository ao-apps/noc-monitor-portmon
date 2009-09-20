package com.aoindustries.aoserv.portmon;
/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.*;
import com.aoindustries.profiler.*;

/**
 * Errors are stored in these objects.
 *
 * @author  AO Industries, Inc.
 */
public final class PortError {
    
    final long time;
    final int errorCode;
    final AOServer aoServer;
    final String ipAddress;
    final int port;
    final String netProtocol;
    final String appProtocol;

    public PortError(int code) {
        Profiler.startProfile(Profiler.FAST, PortError.class, "<init>(int)", null);
        try {
            this.time=System.currentTimeMillis();
            this.errorCode=code;
            this.aoServer=null;
            this.ipAddress=null;
            this.port=-1;
            this.netProtocol=null;
            this.appProtocol=null;
        } finally {
            Profiler.endProfile(Profiler.FAST);
        }
    }
    
    public PortError(
        int code,
        AOServer aoServer,
        String ipAddress,
        int port,
        String netProtocol,
        String appProtocol
    ) {
        Profiler.startProfile(Profiler.FAST, PortError.class, "<init>(int,AOServer,String,int,String,String)", null);
        try {
            this.time=System.currentTimeMillis();
            this.errorCode=code;
            this.aoServer=aoServer;
            this.ipAddress=ipAddress;
            this.port=port;
            this.netProtocol=netProtocol;
            this.appProtocol=appProtocol;
        } finally {
            Profiler.endProfile(Profiler.FAST);
        }
    }
}