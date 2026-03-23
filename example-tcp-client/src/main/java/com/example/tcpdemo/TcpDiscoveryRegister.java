package com.example.tcpdemo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Sends JSON registration to discovery's TCP port (same payload as HTTP /api/register body).
 * Client must {@link Socket#shutdownOutput()} so the server reads until EOF.
 */
public final class TcpDiscoveryRegister {

    private TcpDiscoveryRegister() {}

    public static String register(String host, int tcpPort, String jsonBody) throws IOException {
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
            JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
            if (o.has("error")) {
                throw new IOException(o.get("error").getAsString());
            }
            if (!o.has("instanceId")) {
                throw new IOException("Invalid TCP response: " + resp);
            }
            return o.get("instanceId").getAsString();
        }
    }
}
