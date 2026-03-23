package com.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServiceInstance {

    private String instanceId;
    private String name;
    private String host;
    private int port;
    private String protocol;
    private Map<String, String> metadata;
    private long lastHeartbeat;
    private long registeredAt;
    private String sourceNodeId;

    public ServiceInstance() {
        this.metadata = new HashMap<>();
        this.protocol = "http";
    }

    public static String generateInstanceId(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getBaseUrl() {
        return protocol + "://" + host + ":" + port;
    }

    @Override
    public String toString() {
        return "ServiceInstance{" + instanceId + " " + name + " " + host + ":" + port + "}";
    }
}
