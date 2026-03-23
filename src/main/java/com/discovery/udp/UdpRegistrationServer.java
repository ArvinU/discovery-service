package com.discovery.udp;

import com.discovery.ServiceInstance;
import com.discovery.ServiceRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                LOG.info("UDP registration server listening on port " + port);
                byte[] buf = new byte[BUFFER_SIZE];

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);

                        String json = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                        ServiceInstance instance = gson.fromJson(json, ServiceInstance.class);

                        JsonObject response = new JsonObject();
                        if (instance.getName() == null || instance.getHost() == null || instance.getPort() <= 0) {
                            response.addProperty("error", "Invalid registration: name, host, and port are required");
                        } else {
                            String instanceId = registry.register(instance);
                            response.addProperty("instanceId", instanceId);
                            response.addProperty("name", instance.getName());
                        }

                        byte[] respBytes = gson.toJson(response).getBytes("UTF-8");
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
}
