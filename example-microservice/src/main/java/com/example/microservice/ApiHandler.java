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

        String path = stripTrailingSlash(exchange.getRequestURI().getPath());

        if ("/api/status".equals(path)) {
            sendJson(exchange, 200, gson.toJson(buildStatus()));
        } else if ("/api/test-info".equals(path)) {
            sendJson(exchange, 200, gson.toJson(buildTestInfo()));
        } else {
            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
        }
    }

    private Map<String, Object> buildTestInfo() {
        long uptimeSec = (System.currentTimeMillis() - startTime) / 1000;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("instanceId", instanceId);
        info.put("port", port);
        info.put("uptimeSeconds", uptimeSec);
        info.put("message", "Hello from instance " + instanceId + " on port " + port);
        return info;
    }

    private Map<String, Object> buildStatus() {
        long uptimeSec = (System.currentTimeMillis() - startTime) / 1000;
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
        return status;
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private static String stripTrailingSlash(String path) {
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) return days + "d " + hours + "h " + mins + "m " + secs + "s";
        if (hours > 0) return hours + "h " + mins + "m " + secs + "s";
        if (mins > 0) return mins + "m " + secs + "s";
        return secs + "s";
    }
}
