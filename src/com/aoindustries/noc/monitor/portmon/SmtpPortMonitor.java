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
 * Monitors with SMTP-specific protocol support.
 *
 * @author  AO Industries, Inc.
 */
public class SmtpPortMonitor extends DefaultTcpPortMonitor {

    private final Map<String,String> monitoringParameters;

    public SmtpPortMonitor(InetAddress ipAddress, NetPort port, Map<String,String> monitoringParameters) {
        super(ipAddress, port);
        this.monitoringParameters = monitoringParameters;
    }

    @Override
    public String checkPort(InputStream socketIn, OutputStream socketOut) throws Exception {
        // Get the configuration
        String from = monitoringParameters.get("from");
        if(from==null || from.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the from parameter");
        String recipient = monitoringParameters.get("recipient");
        if(recipient==null || recipient.length()==0) throw new IllegalArgumentException("monitoringParameters does not include the recipient parameter");

        Charset charset = Charset.forName("US-ASCII");
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socketOut, charset));
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socketIn, charset));
            try {
                // Status line
                String line = in.readLine();
                if(line==null) throw new EOFException("End of file reading status");
                if(!line.startsWith("220 ")) throw new IOException("Unexpected status line: "+line);
                // HELO
                out.println("HELO localhost");
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading HELO response");
                if(!line.startsWith("250 ")) throw new IOException("Unexpected line reading HELO response: "+line);
                // MAIL From
                out.println("MAIL From:"+from);
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading MAIL From response");
                if(!line.startsWith("250 2.1.0 ")) throw new IOException("Unexpected line reading MAIL From response: "+line);
                // RCPT To
                out.println("RCPT To:"+recipient);
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading RCPT To response");
                if(!line.startsWith("250 2.1.5 ")) throw new IOException("Unexpected line reading RCPT To response: "+line);
                // DATA
                out.println("DATA");
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading DATA response");
                if(!line.startsWith("354 ")) throw new IOException("Unexpected line reading DATA response: "+line);
                // Message headers and body
                out.println("To: "+recipient);
                out.println("From: "+from);
                out.println("Subject: SMTP monitoring message");
                out.println();
                out.println("This message is generated for SMTP port monitoring.");
                out.println(".");
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading DATA response");
                if(!line.startsWith("250 2.0.0 ")) throw new IOException("Unexpected line reading DATA response: "+line);
                String result = line.substring(10);
                // Quit
                out.println("QUIT");
                out.flush();
                line = in.readLine();
                if(line==null) throw new EOFException("End of file reading QUIT response");
                if(!line.startsWith("221 2.0.0 ")) throw new IOException("Unexpected line reading QUIT response: "+line);
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
