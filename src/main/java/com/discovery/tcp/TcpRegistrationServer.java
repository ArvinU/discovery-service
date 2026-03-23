package com.discovery.tcp;

import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                LOG.info("TCP registration server listening on port " + port);
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
            ServiceInstance instance = gson.fromJson(json, ServiceInstance.class);

            JsonObject response = new JsonObject();
            if (instance.getName() == null || instance.getHost() == null || instance.getPort() <= 0) {
                response.addProperty("error", "Invalid registration: name, host, and port are required");
            } else {
                String instanceId = registry.register(instance);
                response.addProperty("instanceId", instanceId);
                response.addProperty("name", instance.getName());
            }

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
