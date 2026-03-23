package com.discovery;

import com.discovery.config.NodeConfig;
import com.discovery.health.HeartbeatMonitor;
import com.discovery.http.HttpApiHandler;
import com.discovery.http.ProxyHandler;
import com.discovery.sync.PeerSyncManager;
import com.discovery.tcp.TcpRegistrationServer;
import com.discovery.udp.UdpRegistrationServer;
import com.discovery.webhook.WebhookNotifier;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class DiscoveryServer {

    private static final Logger LOG = Logger.getLogger(DiscoveryServer.class.getName());

    public static void main(String[] args) throws IOException {
        String configPath = resolveConfigPath(args);
        if (configPath == null) {
            System.err.println("Usage: java -jar discovery-service.jar --config <path-to-config.json>");
            System.err.println("   Or: set DISCOVERY_CONFIG environment variable");
            System.err.println();
            System.err.println("Example: java -jar discovery-service.jar --config config/local/node-1.json");
            System.exit(1);
        }

        NodeConfig config = NodeConfig.load(configPath);
        LOG.info("Starting discovery service: " + config);

        String dataDir = config.getData().getDir();
        String nodeId = config.getNodeId();

        long ttlMs = config.getHeartbeat().getTtlMs();
        ServiceRegistry registry = new ServiceRegistry(dataDir, nodeId, ttlMs);

        WebhookNotifier webhookNotifier = new WebhookNotifier(dataDir);
        registry.addChangeListener(webhookNotifier);

        for (String url : config.getWebhooks()) {
            if (url != null && !url.trim().isEmpty()) {
                webhookNotifier.addSubscriber(url.trim());
            }
        }

        PeerSyncManager syncManager = new PeerSyncManager(
                registry, nodeId, config.getPeers(),
                config.getSync().getIntervalMs(),
                config.getSync().getTimeoutMs());
        registry.addChangeListener(syncManager);

        int httpPort = config.getHttp().getPort();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        httpServer.createContext("/api/", new HttpApiHandler(
                registry, webhookNotifier, nodeId,
                config.getEnvironment(), config.getPeers()));
        httpServer.createContext("/proxy/", new ProxyHandler(registry));
        httpServer.setExecutor(Executors.newFixedThreadPool(config.getHttp().getThreads()));
        httpServer.start();
        LOG.info("HTTP server started on port " + httpPort);

        int tcpPort = config.getTcp().getPort();
        TcpRegistrationServer tcpServer = new TcpRegistrationServer(tcpPort, registry);
        tcpServer.start();
        LOG.info("TCP protocol server started on port " + tcpPort);

        int udpPort = config.getUdp().getPort();
        UdpRegistrationServer udpServer = new UdpRegistrationServer(udpPort, registry);
        udpServer.start();
        LOG.info("UDP protocol server started on port " + udpPort);

        long checkMs = config.getHeartbeat().getCheckIntervalMs();
        HeartbeatMonitor monitor = new HeartbeatMonitor(registry, ttlMs, checkMs);
        monitor.start();
        LOG.info("Heartbeat monitor started (TTL=" + ttlMs + "ms, interval=" + checkMs + "ms)");

        syncManager.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down discovery service [" + nodeId + "]...");
            httpServer.stop(2);
            tcpServer.stop();
            udpServer.stop();
            monitor.stop();
            syncManager.stop();
            webhookNotifier.shutdown();
            registry.shutdown();
            LOG.info("Shutdown complete");
        }));

        LOG.info("Discovery service [" + nodeId + "] ready — HTTP:" + httpPort
                + " TCP:" + tcpPort + " UDP:" + udpPort
                + " peers:" + config.getPeers().size());
    }

    private static String resolveConfigPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                return args[i + 1];
            }
        }
        String env = System.getenv("DISCOVERY_CONFIG");
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return null;
    }
}
