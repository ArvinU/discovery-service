package com.example.microservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("js", "application/javascript; charset=utf-8");
        MIME_TYPES.put("json", "application/json; charset=utf-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
    }

    private final String resourceBase;

    public StaticFileHandler(String resourceBase) {
        this.resourceBase = resourceBase.endsWith("/") ? resourceBase : resourceBase + "/";
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        String resourcePath = resourceBase + path.substring(1);
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

        if (is == null) {
            is = getClass().getClassLoader().getResourceAsStream(resourceBase + "index.html");
            if (is == null) {
                String msg = "Not found";
                byte[] bytes = msg.getBytes("UTF-8");
                exchange.sendResponseHeaders(404, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            resourcePath = resourceBase + "index.html";
        }

        String ext = getExtension(resourcePath);
        String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        byte[] data = readAll(is);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
        exchange.close();
    }

    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        is.close();
        return bos.toByteArray();
    }
}
