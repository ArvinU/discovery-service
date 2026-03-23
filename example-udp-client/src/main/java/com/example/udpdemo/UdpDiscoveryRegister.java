package com.example.udpdemo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Discovery UDP port: register, heartbeat, and deregister (same JSON protocol as the server).
 */
public final class UdpDiscoveryRegister {

    private static final Gson GSON = new Gson();

    private UdpDiscoveryRegister() {}

    public static String register(String host, int udpPort, String jsonBody) throws IOException {
        JsonObject r = udpRoundTrip(host, udpPort, jsonBody);
        if (r.has("error")) {
            throw new IOException(r.get("error").getAsString());
        }
        if (!r.has("instanceId")) {
            throw new IOException("Invalid UDP response: " + r);
        }
        return r.get("instanceId").getAsString();
    }

    public static void heartbeat(String host, int udpPort, String instanceId) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("op", "heartbeat");
        req.addProperty("instanceId", instanceId);
        JsonObject r = udpRoundTrip(host, udpPort, GSON.toJson(req));
        if (r.has("error")) {
            throw new IOException(r.get("error").getAsString());
        }
        if (!r.has("ok") || !r.get("ok").getAsBoolean()) {
            throw new IOException("Unexpected response: " + r);
        }
    }

    public static void deregister(String host, int udpPort, String instanceId) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("op", "deregister");
        req.addProperty("instanceId", instanceId);
        JsonObject r = udpRoundTrip(host, udpPort, GSON.toJson(req));
        if (r.has("error")) {
            throw new IOException(r.get("error").getAsString());
        }
        if (!r.has("ok") || !r.get("ok").getAsBoolean()) {
            throw new IOException("Unexpected response: " + r);
        }
    }

    private static JsonObject udpRoundTrip(String host, int udpPort, String jsonBody) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(15000);
            byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(host);
            socket.send(new DatagramPacket(data, data.length, addr, udpPort));

            byte[] buf = new byte[8192];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            socket.receive(recv);

            String resp = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
            return JsonParser.parseString(resp).getAsJsonObject();
        }
    }
}
