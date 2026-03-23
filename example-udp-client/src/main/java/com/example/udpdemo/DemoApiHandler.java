package com.example.udpdemo;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

public class DemoApiHandler implements HttpHandler {

    private final Gson gson = new Gson();
    private final String registrationChannel;
    private final String instanceId;
    private final int port;
    private final long startTime;

    public DemoApiHandler(String registrationChannel, String instanceId, int port) {
        this.registrationChannel = registrationChannel;
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

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        String path = exchange.getRequestURI().getPath();

        if ("/api/status".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleStatus(exchange);
        } else if ("/api/procman-info".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleProcmanInfo(exchange);
        } else {
            byte[] bytes = "{\"error\":\"Not found\"}".getBytes("UTF-8");
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    private void handleProcmanInfo(HttpExchange exchange) throws IOException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("registrationChannel", registrationChannel);
        info.put("instanceId", instanceId);
        info.put("port", port);
        info.put("uptimeSeconds", (System.currentTimeMillis() - startTime) / 1000);
        info.put("message", "Registered via " + registrationChannel.toUpperCase() + " — instance " + instanceId);
        writeJson(exchange, 200, info);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        long uptimeSec = (System.currentTimeMillis() - startTime) / 1000;
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("registrationChannel", registrationChannel);
        status.put("instanceId", instanceId);
        status.put("port", port);
        status.put("uptimeSeconds", uptimeSec);
        status.put("uptimeFormatted", formatUptime(uptimeSec));
        status.put("javaVersion", System.getProperty("java.version"));
        status.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        status.put("maxMemoryMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        status.put("freeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));
        status.put("pid", ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        writeJson(exchange, 200, status);
    }

    private void writeJson(HttpExchange exchange, int code, Map<String, Object> map) throws IOException {
        String json = gson.toJson(map);
        byte[] bytes = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String formatUptime(long seconds) {
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (seconds >= 3600) {
            return hours + "h " + mins + "m " + secs + "s";
        }
        if (seconds >= 60) {
            return mins + "m " + secs + "s";
        }
        return secs + "s";
    }
}
