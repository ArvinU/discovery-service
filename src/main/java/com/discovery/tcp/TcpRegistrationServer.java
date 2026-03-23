package com.discovery.tcp;

import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP JSON protocol (one request per connection, client closes write side after payload):
 * <ul>
 *   <li><b>Register</b> (legacy): body is a {@link ServiceInstance} JSON with {@code name}, {@code host}, {@code port} — no {@code op} field.</li>
 *   <li><b>Register</b> (explicit): {@code {"op":"register","name":"...","host":"...","port":n,...}}</li>
 *   <li><b>Heartbeat</b>: {@code {"op":"heartbeat","instanceId":"..."}}</li>
 *   <li><b>Deregister</b>: {@code {"op":"deregister","instanceId":"..."}}</li>
 * </ul>
 */
public class TcpRegistrationServer {

    private static final Logger LOG = Logger.getLogger(TcpRegistrationServer.class.getName());

    private final int port;
    private final ServiceRegistry registry;
    private final Gson gson = new Gson();
    private final ExecutorService executor;
    private volatile ServerSocket serverSocket;
    private volatile boolean running = false;

    public TcpRegistrationServer(int port, ServiceRegistry registry) {
        this.port = port;
        this.registry = registry;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "tcp-handler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        Thread acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                LOG.info("TCP protocol server listening on port " + port);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        executor.submit(() -> handleClient(client));
                    } catch (SocketException e) {
                        if (running) {
                            LOG.log(Level.WARNING, "TCP accept error", e);
                        }
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "TCP server failed to start on port " + port, e);
            }
        }, "tcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error closing TCP server socket", e);
        }
        executor.shutdown();
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(10000);
            String json = readUntilEof(client.getInputStream());
            JsonObject response = handleMessage(json);
            byte[] responseBytes = gson.toJson(response).getBytes("UTF-8");
            client.getOutputStream().write(responseBytes);
            client.getOutputStream().flush();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "TCP client handling error", e);
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
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

    private static String readUntilEof(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }
}
