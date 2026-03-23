package com.example.udpdemo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Demo: register with discovery over <strong>UDP</strong> (JSON datagram),
 * then keep the lease with HTTP heartbeats. Serves a small HTTP UI for ProcMan proxy.
 */
public class UdpDemoApp {

    private static final Logger LOG = Logger.getLogger(UdpDemoApp.class.getName());
    private static final String FRONTEND = "frontend/";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "9102"));
        String discoveryUrl = env("DISCOVERY_URL", "http://localhost:8500");
        String udpHost = env("DISCOVERY_UDP_HOST", "localhost");
        int udpPort = Integer.parseInt(env("DISCOVERY_UDP_PORT", "8502"));
        String serviceName = env("SERVICE_NAME", "udp-demo-service");
        String host = env("SERVICE_HOST", "localhost");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("procman", "true");
        metadata.put("uiPath", "/");
        metadata.put("description", "UDP registration demo");

        JsonObject body = new JsonObject();
        body.addProperty("name", serviceName);
        body.addProperty("host", host);
        body.addProperty("port", port);
        body.addProperty("protocol", "http");
        body.add("metadata", GSON.toJsonTree(metadata));

        String instanceId;
        try {
            instanceId = UdpDiscoveryRegister.register(udpHost, udpPort, GSON.toJson(body));
            LOG.info("Registered via UDP as: " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "UDP registration failed (is discovery running on " + udpHost + ":" + udpPort + "?)", e);
            instanceId = "unregistered-" + port;
        }

        HttpLifecycle lifecycle = new HttpLifecycle(discoveryUrl);
        if (!instanceId.startsWith("unregistered-")) {
            lifecycle.start(instanceId);
        }

        DemoApiHandler api = new DemoApiHandler("udp", instanceId, port);
        RootDispatcher root = new RootDispatcher(api, new StaticFileHandler(FRONTEND));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", root);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        LOG.info("UDP demo HTTP UI on port " + port + " — ProcMan filter: procman=true");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(2);
            lifecycle.shutdown();
        }));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : def;
    }
}
