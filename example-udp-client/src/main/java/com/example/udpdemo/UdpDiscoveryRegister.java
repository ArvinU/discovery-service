package com.example.udpdemo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Sends JSON registration in one datagram; reads JSON response from discovery's UDP port.
 */
public final class UdpDiscoveryRegister {

    private UdpDiscoveryRegister() {}

    public static String register(String host, int udpPort, String jsonBody) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(15000);
            byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(host);
            DatagramPacket send = new DatagramPacket(data, data.length, addr, udpPort);
            socket.send(send);

            byte[] buf = new byte[8192];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            socket.receive(recv);

            String resp = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
            if (o.has("error")) {
                throw new IOException(o.get("error").getAsString());
            }
            if (!o.has("instanceId")) {
                throw new IOException("Invalid UDP response: " + resp);
            }
            return o.get("instanceId").getAsString();
        }
    }
}
