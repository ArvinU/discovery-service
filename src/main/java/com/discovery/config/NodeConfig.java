package com.discovery.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class NodeConfig {

    private static final Logger LOG = Logger.getLogger(NodeConfig.class.getName());

    private String nodeId = "node-1";
    private String environment = "local";
    private HttpConfig http = new HttpConfig();
    private TcpConfig tcp = new TcpConfig();
    private UdpConfig udp = new UdpConfig();
    private HeartbeatConfig heartbeat = new HeartbeatConfig();
    private DataConfig data = new DataConfig();
    private SyncConfig sync = new SyncConfig();
    private List<String> webhooks = new ArrayList<>();
    private List<String> peers = new ArrayList<>();

    public static class HttpConfig {
        private int port = 8500;
        private int threads = 20;

        public int getPort() { return port; }
        public int getThreads() { return threads; }
    }

    public static class TcpConfig {
        private int port = 8501;

        public int getPort() { return port; }
    }

    public static class UdpConfig {
        private int port = 8502;

        public int getPort() { return port; }
    }

    public static class HeartbeatConfig {
        private long ttlMs = 90000;
        private long checkIntervalMs = 30000;

        public long getTtlMs() { return ttlMs; }
        public long getCheckIntervalMs() { return checkIntervalMs; }
    }

    public static class DataConfig {
        private String dir = "data";

        public String getDir() { return dir; }
    }

    public static class SyncConfig {
        private long intervalMs = 5000;
        private int timeoutMs = 5000;

        public long getIntervalMs() { return intervalMs; }
        public int getTimeoutMs() { return timeoutMs; }
    }

    public String getNodeId() { return nodeId; }
    public String getEnvironment() { return environment; }
    public HttpConfig getHttp() { return http; }
    public TcpConfig getTcp() { return tcp; }
    public UdpConfig getUdp() { return udp; }
    public HeartbeatConfig getHeartbeat() { return heartbeat; }
    public DataConfig getData() { return data; }
    public SyncConfig getSync() { return sync; }
    public List<String> getWebhooks() { return webhooks; }
    public List<String> getPeers() { return peers; }

    public static NodeConfig load(String path) throws IOException {
        Gson gson = new GsonBuilder().create();
        try (Reader reader = new BufferedReader(new FileReader(path))) {
            NodeConfig config = gson.fromJson(reader, NodeConfig.class);
            if (config == null) {
                throw new IOException("Empty or invalid config file: " + path);
            }
            config.validate();
            LOG.info("Loaded config from " + path + " [node=" + config.nodeId
                    + ", env=" + config.environment + "]");
            return config;
        }
    }

    private void validate() {
        if (nodeId == null || nodeId.isEmpty()) {
            throw new IllegalArgumentException("nodeId is required in config");
        }
        if (environment == null || environment.isEmpty()) {
            throw new IllegalArgumentException("environment is required in config");
        }
        if (http.port <= 0 || http.port > 65535) {
            throw new IllegalArgumentException("http.port must be between 1 and 65535");
        }
        if (tcp.port <= 0 || tcp.port > 65535) {
            throw new IllegalArgumentException("tcp.port must be between 1 and 65535");
        }
        if (udp.port <= 0 || udp.port > 65535) {
            throw new IllegalArgumentException("udp.port must be between 1 and 65535");
        }
    }

    @Override
    public String toString() {
        return "NodeConfig{nodeId='" + nodeId + "', env='" + environment
                + "', http=" + http.port + ", tcp=" + tcp.port + ", udp=" + udp.port
                + ", peers=" + peers.size() + "}";
    }
}
