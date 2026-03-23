package com.discovery.udp;

import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UDP JSON protocol (one datagram per request/response):
 * Same message shapes as {@link com.discovery.tcp.TcpRegistrationServer} — register / heartbeat / deregister.
 */
public class UdpRegistrationServer {

    private static final Logger LOG = Logger.getLogger(UdpRegistrationServer.class.getName());
    private static final int BUFFER_SIZE = 4096;

    private final int port;
    private final ServiceRegistry registry;
    private final Gson gson = new Gson();
    private volatile DatagramSocket socket;
    private volatile boolean running = false;

    public UdpRegistrationServer(int port, ServiceRegistry registry) {
        this.port = port;
        this.registry = registry;
    }

    public void start() {
        running = true;
        Thread thread = new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                LOG.info("UDP protocol server listening on port " + port);
                byte[] buf = new byte[BUFFER_SIZE];

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);

                        String json = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                        JsonObject response = handleMessage(json);

                        byte[] respBytes = gson.toJson(response).getBytes("UTF-8");
                        if (respBytes.length > BUFFER_SIZE) {
                            LOG.warning("UDP response too large, truncating");
                            respBytes = java.util.Arrays.copyOf(respBytes, BUFFER_SIZE);
                        }
                        InetAddress clientAddr = packet.getAddress();
                        int clientPort = packet.getPort();
                        DatagramPacket respPacket = new DatagramPacket(respBytes, respBytes.length, clientAddr, clientPort);
                        socket.send(respPacket);

                    } catch (SocketException e) {
                        if (running) {
                            LOG.log(Level.WARNING, "UDP receive error", e);
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "UDP handling error", e);
                    }
                }
            } catch (SocketException e) {
                LOG.log(Level.SEVERE, "UDP server failed to start on port " + port, e);
            }
        }, "udp-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }

    private JsonObject handleMessage(String json) {
        JsonObject response = new JsonObject();
        if (json == null || json.trim().isEmpty()) {
            response.addProperty("error", "Empty body");
            return response;
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("op")) {
                String op = obj.get("op").getAsString().trim().toLowerCase();
                switch (op) {
                    case "heartbeat":
                        return handleHeartbeat(obj);
                    case "deregister":
                        return handleDeregister(obj);
                    case "register":
                        return handleRegisterJson(obj);
                    default:
                        response.addProperty("error", "Unknown op: " + op);
                        return response;
                }
            }
            return handleRegisterLegacy(json);
        } catch (JsonSyntaxException e) {
            response.addProperty("error", "Invalid JSON");
            return response;
        }
    }

    private JsonObject handleHeartbeat(JsonObject obj) {
        JsonObject response = new JsonObject();
        if (!obj.has("instanceId") || obj.get("instanceId").getAsString().isEmpty()) {
            response.addProperty("error", "instanceId is required for heartbeat");
            return response;
        }
        String id = obj.get("instanceId").getAsString();
        if (registry.heartbeat(id)) {
            response.addProperty("ok", true);
        } else {
            response.addProperty("error", "Instance not found: " + id);
        }
        return response;
    }

    private JsonObject handleDeregister(JsonObject obj) {
        JsonObject response = new JsonObject();
        if (!obj.has("instanceId") || obj.get("instanceId").getAsString().isEmpty()) {
            response.addProperty("error", "instanceId is required for deregister");
            return response;
        }
        String id = obj.get("instanceId").getAsString();
        if (registry.deregister(id)) {
            response.addProperty("ok", true);
        } else {
            response.addProperty("error", "Instance not found: " + id);
        }
        return response;
    }

    private JsonObject handleRegisterJson(JsonObject obj) {
        ServiceInstance instance = gson.fromJson(obj, ServiceInstance.class);
        return finishRegister(instance);
    }

    private JsonObject handleRegisterLegacy(String json) {
        ServiceInstance instance = gson.fromJson(json, ServiceInstance.class);
        return finishRegister(instance);
    }

    private JsonObject finishRegister(ServiceInstance instance) {
        JsonObject response = new JsonObject();
        if (instance.getName() == null || instance.getHost() == null || instance.getPort() <= 0) {
            response.addProperty("error", "Invalid registration: name, host, and port are required");
        } else {
            String instanceId = registry.register(instance);
            response.addProperty("instanceId", instanceId);
            response.addProperty("name", instance.getName());
        }
        return response;
    }
}
