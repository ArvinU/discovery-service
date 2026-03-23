package com.discovery.webhook;

import com.discovery.ChangeEvent;
import com.discovery.ServiceRegistry.RegistryChangeListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebhookNotifier implements RegistryChangeListener {

    private static final Logger LOG = Logger.getLogger(WebhookNotifier.class.getName());
    private static final String WEBHOOKS_FILE = "webhooks.json";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final CopyOnWriteArrayList<String> subscribers = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;
    private final Gson gson;
    private final String dataDir;

    public WebhookNotifier(String dataDir) {
        this.dataDir = dataDir;
        this.gson = new GsonBuilder().create();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "webhook-notifier");
            t.setDaemon(true);
            return t;
        });
        loadSubscribers();
    }

    public void addSubscriber(String url) {
        if (!subscribers.contains(url)) {
            subscribers.add(url);
            persistSubscribers();
            LOG.info("Added webhook subscriber: " + url);
        }
    }

    public boolean removeSubscriber(String url) {
        boolean removed = subscribers.remove(url);
        if (removed) {
            persistSubscribers();
            LOG.info("Removed webhook subscriber: " + url);
        }
        return removed;
    }

    public List<String> getSubscribers() {
        return Collections.unmodifiableList(new ArrayList<>(subscribers));
    }

    @Override
    public void onChange(ChangeEvent event) {
        if (subscribers.isEmpty()) {
            return;
        }
        String payload = gson.toJson(event);
        for (String url : subscribers) {
            executor.submit(() -> deliver(url, payload));
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void deliver(String webhookUrl, String payload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                LOG.fine("Webhook delivered to " + webhookUrl + " (HTTP " + code + ")");
            } else {
                LOG.warning("Webhook delivery to " + webhookUrl + " returned HTTP " + code);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Webhook delivery failed for " + webhookUrl + ": " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void persistSubscribers() {
        try {
            File target = new File(dataDir, WEBHOOKS_FILE);
            File tmp = new File(dataDir, WEBHOOKS_FILE + ".tmp");
            try (Writer writer = new BufferedWriter(new FileWriter(tmp))) {
                writer.write(gson.toJson(new ArrayList<>(subscribers)));
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to persist webhooks", e);
        }
    }

    private void loadSubscribers() {
        File file = new File(dataDir, WEBHOOKS_FILE);
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new BufferedReader(new FileReader(file))) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                subscribers.addAll(loaded);
                LOG.info("Loaded " + loaded.size() + " webhook subscribers from disk");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load webhooks from file", e);
        }
    }
}
