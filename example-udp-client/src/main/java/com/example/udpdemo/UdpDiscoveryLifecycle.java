package com.example.udpdemo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a UDP-registered instance alive using only the discovery <strong>UDP</strong> port.
 */
public class UdpDiscoveryLifecycle {

    private static final Logger LOG = Logger.getLogger(UdpDiscoveryLifecycle.class.getName());

    private final String udpHost;
    private final int udpPort;
    private final long heartbeatIntervalSec;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "udp-discovery-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private volatile String instanceId;

    public UdpDiscoveryLifecycle(String udpHost, int udpPort, long heartbeatIntervalSec) {
        this.udpHost = udpHost;
        this.udpPort = udpPort;
        this.heartbeatIntervalSec = heartbeatIntervalSec;
    }

    public void start(String instanceId) {
        this.instanceId = instanceId;
        scheduler.scheduleAtFixedRate(this::heartbeat, heartbeatIntervalSec, heartbeatIntervalSec, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::deregister, "udp-discovery-deregister"));
    }

    public void shutdown() {
        scheduler.shutdown();
        deregister();
    }

    private void heartbeat() {
        if (instanceId == null) return;
        try {
            UdpDiscoveryRegister.heartbeat(udpHost, udpPort, instanceId);
            LOG.fine("UDP heartbeat " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "UDP heartbeat failed: " + e.getMessage());
        }
    }

    private void deregister() {
        if (instanceId == null) return;
        try {
            UdpDiscoveryRegister.deregister(udpHost, udpPort, instanceId);
            LOG.info("UDP deregistered " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "UDP deregister failed: " + e.getMessage());
        }
    }
}
