package com.example.tcpdemo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Discovery TCP port: register, heartbeat, and deregister (same JSON protocol as the server).
 */
public final class TcpDiscoveryRegister {

    private static final Gson GSON = new Gson();

    private TcpDiscoveryRegister() {}

    public static String register(String host, int tcpPort, String jsonBody) throws IOException {
        JsonObject r = tcpRoundTrip(host, tcpPort, jsonBody);
        if (r.has("error")) {
            throw new IOException(r.get("error").getAsString());
        }
        if (!r.has("instanceId")) {
            throw new IOException("Invalid TCP response: " + r);
        }
        return r.get("instanceId").getAsString();
    }

    public static void heartbeat(String host, int tcpPort, String instanceId) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("op", "heartbeat");
        req.addProperty("instanceId", instanceId);
        JsonObject r = tcpRoundTrip(host, tcpPort, GSON.toJson(req));
        if (r.has("error")) {
            throw new IOException(r.get("error").getAsString());
        }
        if (!r.has("ok") || !r.get("ok").getAsBoolean()) {
            throw new IOException("Unexpected response: " + r);
        }
    }

    public static void deregister(String host, int tcpPort, String instanceId) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("op", "deregister");
        req.addProperty("instanceId", instanceId);
        JsonObject r = tcpRoundTrip(host, tcpPort, GSON.toJson(req));
        if (r.has("error")) {
            throw new IOException(r.get("error").getAsString());
        }
        if (!r.has("ok") || !r.get("ok").getAsBoolean()) {
            throw new IOException("Unexpected response: " + r);
        }
    }

    private static JsonObject tcpRoundTrip(String host, int tcpPort, String jsonBody) throws IOException {
        try (Socket socket = new Socket(host, tcpPort)) {
            socket.setSoTimeout(15000);
            OutputStream out = socket.getOutputStream();
            out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            out.flush();
            socket.shutdownOutput();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            InputStream in = socket.getInputStream();
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }

            String resp = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            return JsonParser.parseString(resp).getAsJsonObject();
        }
    }
}
