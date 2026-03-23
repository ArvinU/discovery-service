package com.discovery.sync;

import com.discovery.ChangeEvent;
import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;
import com.discovery.ServiceRegistry.RegistryChangeListener;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeerSyncManager implements RegistryChangeListener {

    private static final Logger LOG = Logger.getLogger(PeerSyncManager.class.getName());

    private final ServiceRegistry registry;
    private final List<String> peerUrls;
    private final String nodeId;
    private final long syncIntervalMs;
    private final int timeoutMs;
    private final Gson gson;
    private final ScheduledExecutorService pullScheduler;
    private final ExecutorService pushExecutor;

    public PeerSyncManager(ServiceRegistry registry, String nodeId, List<String> peerUrls,
                           long syncIntervalMs, int timeoutMs) {
        this.registry = registry;
        this.nodeId = nodeId;
        this.peerUrls = new ArrayList<>(peerUrls);
        this.syncIntervalMs = syncIntervalMs;
        this.timeoutMs = timeoutMs;
        this.gson = new GsonBuilder().create();

        this.pullScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "peer-pull-sync");
            t.setDaemon(true);
            return t;
        });
        this.pushExecutor = Executors.newFixedThreadPool(
                Math.max(2, peerUrls.size()), r -> {
                    Thread t = new Thread(r, "peer-push");
                    t.setDaemon(true);
                    return t;
                });
    }

    public void start() {
        if (peerUrls.isEmpty()) {
            LOG.info("No peers configured — sync disabled");
            return;
        }
        pullScheduler.scheduleAtFixedRate(this::pullFromAllPeers,
                syncIntervalMs, syncIntervalMs, TimeUnit.MILLISECONDS);
        LOG.info("Peer sync started with " + peerUrls.size() + " peer(s), interval=" + syncIntervalMs + "ms");
    }

    @Override
    public void onChange(ChangeEvent event) {
        if (event.isFromSync()) {
            return;
        }
        for (String peerUrl : peerUrls) {
            pushExecutor.submit(() -> pushEvent(peerUrl, event));
        }
    }

    public void stop() {
        pullScheduler.shutdown();
        pushExecutor.shutdown();
        try {
            pullScheduler.awaitTermination(3, TimeUnit.SECONDS);
            pushExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pullFromAllPeers() {
        for (String peerUrl : peerUrls) {
            try {
                pullFromPeer(peerUrl);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Pull sync failed for " + peerUrl, e);
            }
        }
    }

    private void pullFromPeer(String peerUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(peerUrl + "/api/sync/state");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int code = conn.getResponseCode();
            if (code != 200) {
                LOG.warning("Pull from " + peerUrl + " returned HTTP " + code);
                return;
            }

            String body = readResponse(conn);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String peerNodeId = json.get("nodeId").getAsString();

            Type listType = new TypeToken<List<ServiceInstance>>() {}.getType();
            List<ServiceInstance> peerInstances = gson.fromJson(json.get("instances"), listType);

            if (peerInstances != null && !peerInstances.isEmpty()) {
                registry.mergeFromPeer(peerInstances);
                LOG.fine("Pulled " + peerInstances.size() + " instances from " + peerNodeId);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Pull from " + peerUrl + " failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void pushEvent(String peerUrl, ChangeEvent event) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(peerUrl + "/api/sync/event");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);

            JsonObject payload = new JsonObject();
            payload.addProperty("sourceNodeId", nodeId);
            payload.addProperty("eventType", event.getEvent().name());
            payload.add("instance", gson.toJsonTree(event.getInstance()));

            byte[] body = gson.toJson(payload).getBytes("UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                LOG.fine("Pushed " + event.getEvent() + " to " + peerUrl);
            } else {
                LOG.warning("Push to " + peerUrl + " returned HTTP " + code);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Push to " + peerUrl + " failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        }
    }
}
