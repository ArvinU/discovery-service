package com.discovery.http;

import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProxyHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(ProxyHandler.class.getName());

    private static final Set<String> HOP_BY_HOP = new HashSet<>(Arrays.asList(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host"
    ));

    private final ServiceRegistry registry;

    public ProxyHandler(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = HttpPathUtil.normalizePath(exchange.getRequestURI().getPath());
        if (!path.startsWith("/proxy/")) {
            sendError(exchange, 404, "Invalid proxy path");
            exchange.close();
            return;
        }
        String remaining = path.substring("/proxy/".length());

        int slashIdx = remaining.indexOf('/');
        String instanceId;
        String targetPath;
        if (slashIdx == -1) {
            instanceId = remaining;
            targetPath = "/";
        } else {
            instanceId = remaining.substring(0, slashIdx);
            targetPath = HttpPathUtil.normalizePath(remaining.substring(slashIdx));
        }

        if (instanceId.isEmpty()) {
            sendError(exchange, 400, "Missing instance ID in proxy path");
            exchange.close();
            return;
        }

        ServiceInstance instance = registry.getInstance(instanceId);
        if (instance == null) {
            sendError(exchange, 404, "Instance not found: " + instanceId);
            exchange.close();
            return;
        }

        String targetUrl = instance.getBaseUrl() + targetPath;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            targetUrl += "?" + query;
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(exchange.getRequestMethod());
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);

            for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
                String key = header.getKey();
                if (!HOP_BY_HOP.contains(key.toLowerCase())) {
                    for (String value : header.getValue()) {
                        conn.addRequestProperty(key, value);
                    }
                }
            }

            String method = exchange.getRequestMethod();
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                conn.setDoOutput(true);
                try (InputStream reqBody = exchange.getRequestBody();
                     OutputStream connOut = conn.getOutputStream()) {
                    copy(reqBody, connOut);
                }
            } else {
                exchange.getRequestBody().close();
            }

            int responseCode = conn.getResponseCode();
            InputStream responseStream = responseCode >= 400
                    ? conn.getErrorStream()
                    : conn.getInputStream();

            if (responseStream == null) {
                responseStream = new ByteArrayInputStream(new byte[0]);
            }

            Headers respHeaders = exchange.getResponseHeaders();
            for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                String key = header.getKey();
                if (key != null && !HOP_BY_HOP.contains(key.toLowerCase()) && !"content-length".equalsIgnoreCase(key)) {
                    for (String value : header.getValue()) {
                        respHeaders.add(key, value);
                    }
                }
            }
            respHeaders.set("Access-Control-Allow-Origin", "*");

            byte[] body = readAll(responseStream);
            exchange.sendResponseHeaders(responseCode, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Proxy error for " + targetUrl, e);
            sendError(exchange, 502, "Proxy error: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            exchange.close();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        return bos.toByteArray();
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String json = "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
