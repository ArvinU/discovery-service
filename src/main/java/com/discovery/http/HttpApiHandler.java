package com.discovery.http;

import com.discovery.ChangeEvent;
import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;
import com.discovery.webhook.WebhookNotifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpApiHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(HttpApiHandler.class.getName());

    private final ServiceRegistry registry;
    private final WebhookNotifier webhookNotifier;
    private final String nodeId;
    private final String environment;
    private final List<String> peers;
    private final Gson gson;

    public HttpApiHandler(ServiceRegistry registry, WebhookNotifier webhookNotifier,
                          String nodeId, String environment, List<String> peers) {
        this.registry = registry;
        this.webhookNotifier = webhookNotifier;
        this.nodeId = nodeId;
        this.environment = environment;
        this.peers = peers;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        addCorsHeaders(exchange.getResponseHeaders());

        if ("OPTIONS".equals(method)) {
            sendResponse(exchange, 204, "");
            return;
        }

        try {
            if ("/api/register".equals(path) && "POST".equals(method)) {
                handleRegister(exchange);
            } else if (path.startsWith("/api/deregister/") && "DELETE".equals(method)) {
                handleDeregister(exchange);
            } else if (path.startsWith("/api/heartbeat/") && "POST".equals(method)) {
                handleHeartbeat(exchange);
            } else if ("/api/services".equals(path) && "GET".equals(method)) {
                handleGetServices(exchange);
            } else if (path.startsWith("/api/instances/") && "GET".equals(method)) {
                handleGetInstance(exchange);
            } else if ("/api/health".equals(path) && "GET".equals(method)) {
                handleHealth(exchange);
            } else if ("/api/webhooks".equals(path)) {
                handleWebhooks(exchange, method);
            } else if ("/api/sync/state".equals(path) && "GET".equals(method)) {
                handleSyncState(exchange);
            } else if ("/api/sync/event".equals(path) && "POST".equals(method)) {
                handleSyncEvent(exchange);
            } else {
                sendJson(exchange, 404, errorJson("Not found"));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling request: " + path, e);
            sendJson(exchange, 500, errorJson("Internal server error"));
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        ServiceInstance instance = gson.fromJson(body, ServiceInstance.class);

        if (instance.getName() == null || instance.getName().isEmpty()) {
            sendJson(exchange, 400, errorJson("'name' is required"));
            return;
        }
        if (instance.getHost() == null || instance.getHost().isEmpty()) {
            sendJson(exchange, 400, errorJson("'host' is required"));
            return;
        }
        if (instance.getPort() <= 0) {
            sendJson(exchange, 400, errorJson("'port' must be positive"));
            return;
        }

        String instanceId = registry.register(instance);

        JsonObject response = new JsonObject();
        response.addProperty("instanceId", instanceId);
        response.addProperty("name", instance.getName());
        sendJson(exchange, 201, gson.toJson(response));
    }

    private void handleDeregister(HttpExchange exchange) throws IOException {
        String instanceId = extractPathParam(exchange.getRequestURI().getPath(), "/api/deregister/");
        boolean removed = registry.deregister(instanceId);
        if (removed) {
            sendJson(exchange, 200, successJson("Deregistered " + instanceId));
        } else {
            sendJson(exchange, 404, errorJson("Instance not found: " + instanceId));
        }
    }

    private void handleHeartbeat(HttpExchange exchange) throws IOException {
        String instanceId = extractPathParam(exchange.getRequestURI().getPath(), "/api/heartbeat/");
        boolean updated = registry.heartbeat(instanceId);
        if (updated) {
            sendJson(exchange, 200, successJson("Heartbeat received"));
        } else {
            sendJson(exchange, 404, errorJson("Instance not found: " + instanceId));
        }
    }

    private void handleGetServices(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getQuery());

        Map<String, List<ServiceInstance>> grouped;
        String nameFilter = queryParams.get("name");

        if (nameFilter != null) {
            List<ServiceInstance> byName = registry.getByName(nameFilter);
            grouped = new LinkedHashMap<>();
            if (!byName.isEmpty()) {
                grouped.put(nameFilter, byName);
            }
        } else {
            String metaKey = null;
            String metaValue = null;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!"name".equals(entry.getKey())) {
                    metaKey = entry.getKey();
                    metaValue = entry.getValue();
                    break;
                }
            }
            if (metaKey != null) {
                grouped = registry.getGroupedByMetadata(metaKey, metaValue);
            } else {
                grouped = registry.getGrouped();
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<ServiceInstance>> entry : grouped.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", entry.getKey());
            group.put("instanceCount", entry.getValue().size());
            group.put("instances", entry.getValue());
            groups.add(group);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("groups", groups);
        sendJson(exchange, 200, gson.toJson(response));
    }

    private void handleGetInstance(HttpExchange exchange) throws IOException {
        String instanceId = extractPathParam(exchange.getRequestURI().getPath(), "/api/instances/");
        ServiceInstance inst = registry.getInstance(instanceId);
        if (inst != null) {
            sendJson(exchange, 200, gson.toJson(inst));
        } else {
            sendJson(exchange, 404, errorJson("Instance not found: " + instanceId));
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("nodeId", nodeId);
        health.put("environment", environment);
        health.put("registeredInstances", registry.size());
        health.put("peers", peers);
        health.put("timestamp", System.currentTimeMillis());
        sendJson(exchange, 200, gson.toJson(health));
    }

    private void handleWebhooks(HttpExchange exchange, String method) throws IOException {
        switch (method) {
            case "GET": {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("subscribers", webhookNotifier.getSubscribers());
                sendJson(exchange, 200, gson.toJson(resp));
                break;
            }
            case "POST": {
                String body = readBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String url = json.has("url") ? json.get("url").getAsString() : null;
                if (url == null || url.isEmpty()) {
                    sendJson(exchange, 400, errorJson("'url' is required"));
                    return;
                }
                webhookNotifier.addSubscriber(url);
                sendJson(exchange, 201, successJson("Webhook subscriber added"));
                break;
            }
            case "DELETE": {
                String body = readBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String url = json.has("url") ? json.get("url").getAsString() : null;
                if (url == null || url.isEmpty()) {
                    sendJson(exchange, 400, errorJson("'url' is required"));
                    return;
                }
                boolean removed = webhookNotifier.removeSubscriber(url);
                if (removed) {
                    sendJson(exchange, 200, successJson("Webhook subscriber removed"));
                } else {
                    sendJson(exchange, 404, errorJson("Subscriber not found"));
                }
                break;
            }
            default:
                sendJson(exchange, 405, errorJson("Method not allowed"));
        }
    }

    private void handleSyncState(HttpExchange exchange) throws IOException {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("nodeId", nodeId);
        state.put("instances", new ArrayList<>(registry.getAllInstances()));
        sendJson(exchange, 200, gson.toJson(state));
    }

    private void handleSyncEvent(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        String sourceNodeId = json.has("sourceNodeId") ? json.get("sourceNodeId").getAsString() : null;
        String eventTypeStr = json.has("eventType") ? json.get("eventType").getAsString() : null;

        if (sourceNodeId == null || eventTypeStr == null || !json.has("instance")) {
            sendJson(exchange, 400, errorJson("sourceNodeId, eventType, and instance are required"));
            return;
        }

        ChangeEvent.EventType eventType;
        try {
            eventType = ChangeEvent.EventType.valueOf(eventTypeStr);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson("Invalid eventType: " + eventTypeStr));
            return;
        }

        ServiceInstance instance = gson.fromJson(json.get("instance"), ServiceInstance.class);
        registry.applyPeerEvent(eventType, instance);

        sendJson(exchange, 200, successJson("Sync event applied"));
    }

    private static void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Max-Age", "86400");
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, code == 204 ? -1 : bytes.length);
        if (code != 204) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, code, json);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        }
    }

    private static String extractPathParam(String path, String prefix) {
        String param = path.substring(prefix.length());
        if (param.endsWith("/")) {
            param = param.substring(0, param.length() - 1);
        }
        return param;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return params;
    }

    private static String errorJson(String message) {
        return "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
    }

    private static String successJson(String message) {
        return "{\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
    }
}
