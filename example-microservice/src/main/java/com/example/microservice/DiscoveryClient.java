package com.example.microservice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscoveryClient {

    private static final Logger LOG = Logger.getLogger(DiscoveryClient.class.getName());

    private final String discoveryUrl;
    private final String serviceName;
    private final String host;
    private final int port;
    private final Map<String, String> metadata;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService heartbeatScheduler;

    private volatile String instanceId;

    public DiscoveryClient(String discoveryUrl, String serviceName, String host, int port, Map<String, String> metadata) {
        this.discoveryUrl = discoveryUrl.endsWith("/") ? discoveryUrl.substring(0, discoveryUrl.length() - 1) : discoveryUrl;
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "discovery-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    public String register() {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", serviceName);
            body.addProperty("host", host);
            body.addProperty("port", port);
            body.addProperty("protocol", "http");
            JsonObject meta = new JsonObject();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                meta.addProperty(entry.getKey(), entry.getValue());
            }
            body.add("metadata", meta);

            String response = post(discoveryUrl + "/api/register", gson.toJson(body));
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            instanceId = resp.get("instanceId").getAsString();
            LOG.info("Registered with discovery service as: " + instanceId);

            heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);

            Runtime.getRuntime().addShutdownHook(new Thread(this::deregister, "discovery-deregister"));

            return instanceId;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register with discovery service at " + discoveryUrl, e);
            return null;
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void shutdown() {
        heartbeatScheduler.shutdown();
        deregister();
    }

    private void sendHeartbeat() {
        if (instanceId == null) return;
        try {
            post(discoveryUrl + "/api/heartbeat/" + instanceId, "");
            LOG.fine("Heartbeat sent for " + instanceId);
        } catch (Exception e) {
            LOG.warning("Heartbeat failed for " + instanceId + ": " + e.getMessage());
        }
    }

    private void deregister() {
        if (instanceId == null) return;
        try {
            delete(discoveryUrl + "/api/deregister/" + instanceId);
            LOG.info("Deregistered: " + instanceId);
        } catch (Exception e) {
            LOG.warning("Deregister failed for " + instanceId + ": " + e.getMessage());
        }
    }

    private String post(String urlStr, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        return readResponse(conn);
    }

    private String delete(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }
}
