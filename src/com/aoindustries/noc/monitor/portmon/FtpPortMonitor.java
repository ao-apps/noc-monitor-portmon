package com.aoindustries.noc.monitor.portmon;

/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.validator.InetAddress;
import com.aoindustries.aoserv.client.validator.NetPort;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Monitors with FTP-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class FtpPortMonitor extends DefaultTcpPortMonitor {

    private final Map<String,String> monitoringParameters;

    public FtpPortMonitor(InetAddress ipAddress, NetPort port, Map<String,String> monitoringParameters) {
        super(ipAddress, port);
        this.monitoringParameters = monitoringParameters;
    }

    @Override
    public String checkPort(InputStream socketIn, OutputStream socketOut) throws Exception {
        // Get the configuration
        String username = monitoringParameters.get("username");
        if(username==null || username.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the username");
        String password = monitoringParameters.get("password");
        if(password==null || password.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the password");

        Charset charset = Charset.forName("US-ASCII");
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socketOut, charset));
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socketIn, charset));
            try {
                // Status line
                String line = in.readLine();
                if(line==null) throw new EOFException("End of file reading status");
                if(!line.startsWith("220 ")) throw new IOException("Unexpected status line: "+line);
                // User
                out.println("user "+username);
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading user response");
                if(!line.startsWith("331 ")) throw new IOException("Unexpected line reading user response: "+line);
                // Pass
                out.println("pass "+password);
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading pass response");
                if(!line.startsWith("230 ")) throw new IOException("Unexpected line reading pass response: "+line);
                String result = line.substring(4);
                // Quit
                out.println("quit");
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading quit response");
                if(!line.startsWith("221 ")) throw new IOException("Unexpected line reading quit response: "+line);
                while((line = in.readLine())!=null) {
                    if(!line.startsWith("221 ")) throw new IOException("Unexpected line reading quit response: "+line);
                }
                // Return OK result
                return result;
            } finally {
                in.close();
            }
        } finally {
            out.close();
        }
    }
}
