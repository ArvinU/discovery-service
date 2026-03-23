package com.example.tcpdemo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a TCP-registered instance alive using only the discovery <strong>TCP</strong> port
 * ({@code op:heartbeat} / {@code op:deregister}).
 */
public class TcpDiscoveryLifecycle {

    private static final Logger LOG = Logger.getLogger(TcpDiscoveryLifecycle.class.getName());

    private final String tcpHost;
    private final int tcpPort;
    private final long heartbeatIntervalSec;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tcp-discovery-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private volatile String instanceId;

    public TcpDiscoveryLifecycle(String tcpHost, int tcpPort, long heartbeatIntervalSec) {
        this.tcpHost = tcpHost;
        this.tcpPort = tcpPort;
        this.heartbeatIntervalSec = heartbeatIntervalSec;
    }

    public void start(String instanceId) {
        this.instanceId = instanceId;
        scheduler.scheduleAtFixedRate(this::heartbeat, heartbeatIntervalSec, heartbeatIntervalSec, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::deregister, "tcp-discovery-deregister"));
    }

    public void shutdown() {
        scheduler.shutdown();
        deregister();
    }

    private void heartbeat() {
        if (instanceId == null) return;
        try {
            TcpDiscoveryRegister.heartbeat(tcpHost, tcpPort, instanceId);
            LOG.fine("TCP heartbeat " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "TCP heartbeat failed: " + e.getMessage());
        }
    }

    private void deregister() {
        if (instanceId == null) return;
        try {
            TcpDiscoveryRegister.deregister(tcpHost, tcpPort, instanceId);
            LOG.info("TCP deregistered " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "TCP deregister failed: " + e.getMessage());
        }
    }
}
