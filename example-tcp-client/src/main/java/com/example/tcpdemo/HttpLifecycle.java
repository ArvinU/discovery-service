package com.example.tcpdemo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Heartbeats and deregistration use discovery's HTTP API (TCP/UDP registration servers do not support heartbeat).
 */
public class HttpLifecycle {

    private static final Logger LOG = Logger.getLogger(HttpLifecycle.class.getName());

    private final String discoveryBaseUrl;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "discovery-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private volatile String instanceId;

    public HttpLifecycle(String discoveryUrl) {
        String u = discoveryUrl.endsWith("/") ? discoveryUrl.substring(0, discoveryUrl.length() - 1) : discoveryUrl;
        this.discoveryBaseUrl = u;
    }

    public void start(String instanceId) {
        this.instanceId = instanceId;
        scheduler.scheduleAtFixedRate(this::heartbeat, 30, 30, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::deregister, "discovery-deregister"));
    }

    public void shutdown() {
        scheduler.shutdown();
        deregister();
    }

    private void heartbeat() {
        if (instanceId == null) return;
        try {
            post(discoveryBaseUrl + "/api/heartbeat/" + instanceId, "");
            LOG.fine("Heartbeat " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Heartbeat failed: " + e.getMessage());
        }
    }

    private void deregister() {
        if (instanceId == null) return;
        try {
            delete(discoveryBaseUrl + "/api/deregister/" + instanceId);
            LOG.info("Deregistered " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Deregister failed: " + e.getMessage());
        }
    }

    private static void post(String urlStr, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        readResponse(conn);
    }

    private static void delete(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        readResponse(conn);
    }

    private static void readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is != null) {
            byte[] buf = new byte[4096];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
        }
    }
}
