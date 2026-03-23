package com.example.tcpdemo;

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
 * Demo: register, heartbeat, and deregister with discovery only over <strong>TCP</strong>.
 * The HTTP server here is only for the TestMan UI / proxy, not for discovery.
 */
public class TcpDemoApp {

    private static final Logger LOG = Logger.getLogger(TcpDemoApp.class.getName());
    private static final String FRONTEND = "frontend/";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "9101"));
        String tcpHost = env("DISCOVERY_TCP_HOST", "localhost");
        int tcpPort = Integer.parseInt(env("DISCOVERY_TCP_PORT", "8501"));
        long heartbeatSec = Long.parseLong(env("DISCOVERY_HEARTBEAT_INTERVAL_SEC", "30"));
        String serviceName = env("SERVICE_NAME", "tcp-demo-service");
        String host = env("SERVICE_HOST", "localhost");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("testman", "true");
        metadata.put("uiPath", "/");
        metadata.put("description", "TCP registration demo");

        JsonObject body = new JsonObject();
        body.addProperty("name", serviceName);
        body.addProperty("host", host);
        body.addProperty("port", port);
        body.addProperty("protocol", "http");
        body.add("metadata", GSON.toJsonTree(metadata));

        String instanceId;
        try {
            instanceId = TcpDiscoveryRegister.register(tcpHost, tcpPort, GSON.toJson(body));
            LOG.info("Registered via TCP as: " + instanceId);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "TCP registration failed (is discovery running on " + tcpHost + ":" + tcpPort + "?)", e);
            instanceId = "unregistered-" + port;
        }

        TcpDiscoveryLifecycle lifecycle = new TcpDiscoveryLifecycle(tcpHost, tcpPort, heartbeatSec);
        if (!instanceId.startsWith("unregistered-")) {
            lifecycle.start(instanceId);
        }

        DemoApiHandler api = new DemoApiHandler("tcp", instanceId, port);
        RootDispatcher root = new RootDispatcher(api, new StaticFileHandler(FRONTEND));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", root);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        LOG.info("TCP demo HTTP UI on port " + port + " — discovery uses TCP " + tcpHost + ":" + tcpPort + "; TestMan lists testman=true");

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
