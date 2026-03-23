package com.example.microservice;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiHandler implements HttpHandler {

    private final Gson gson = new Gson();
    private final String instanceId;
    private final int port;
    private final long startTime;

    public ApiHandler(String instanceId, int port) {
        this.instanceId = instanceId;
        this.port = port;
        this.startTime = System.currentTimeMillis();
    }

    public void setInstanceId(String instanceId) {
        // Allow updating after discovery registration
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/json");

        String path = exchange.getRequestURI().getPath();

        if ("/api/status".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleStatus(exchange);
        } else if ("/api/procman-info".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleProcmanInfo(exchange);
        } else {
            String json = "{\"error\":\"Not found\"}";
            byte[] bytes = json.getBytes("UTF-8");
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    /**
     * Simple JSON for ProcMan (parent app) to fetch via discovery proxy.
     * GET /api/procman-info
     */
    private void handleProcmanInfo(HttpExchange exchange) throws IOException {
        long uptimeSec = (System.currentTimeMillis() - startTime) / 1000;

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("instanceId", instanceId);
        info.put("port", port);
        info.put("uptimeSeconds", uptimeSec);
        info.put("message", "Hello from instance " + instanceId + " on port " + port);

        String json = gson.toJson(info);
        byte[] bytes = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        long uptimeMs = System.currentTimeMillis() - startTime;
        long uptimeSec = uptimeMs / 1000;

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("instanceId", instanceId);
        status.put("port", port);
        status.put("uptimeSeconds", uptimeSec);
        status.put("uptimeFormatted", formatUptime(uptimeSec));
        status.put("javaVersion", System.getProperty("java.version"));
        status.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        status.put("maxMemoryMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        status.put("freeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));
        status.put("pid", ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);

        String json = gson.toJson(status);
        byte[] bytes = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + mins + "m " + secs + "s";
        } else if (hours > 0) {
            return hours + "h " + mins + "m " + secs + "s";
        } else if (mins > 0) {
            return mins + "m " + secs + "s";
        }
        return secs + "s";
    }
}
