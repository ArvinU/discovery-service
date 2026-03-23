package com.discovery.health;

import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class HeartbeatMonitor {

    private static final Logger LOG = Logger.getLogger(HeartbeatMonitor.class.getName());

    private final ServiceRegistry registry;
    private final long ttlMs;
    private final long checkIntervalMs;
    private final ScheduledExecutorService scheduler;

    public HeartbeatMonitor(ServiceRegistry registry, long ttlMs, long checkIntervalMs) {
        this.registry = registry;
        this.ttlMs = ttlMs;
        this.checkIntervalMs = checkIntervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::check, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void check() {
        long now = System.currentTimeMillis();
        for (String instanceId : registry.getInstanceIds()) {
            ServiceInstance inst = registry.getInstance(instanceId);
            if (inst != null && (now - inst.getLastHeartbeat()) > ttlMs) {
                LOG.warning("Evicting " + inst + " (last heartbeat " + (now - inst.getLastHeartbeat()) + "ms ago)");
                registry.evict(instanceId);
            }
        }
    }
}
