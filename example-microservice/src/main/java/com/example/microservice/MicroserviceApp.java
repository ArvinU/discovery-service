package com.example.microservice;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Example microservice: small JSON {@linkplain ApiHandler backend} + static {@code frontend/} UI
 * served by one process. The UI calls the API with relative URLs so it works behind the discovery
 * reverse proxy ({@code /proxy/{instanceId}/...}) for TestMan.
 */
public class MicroserviceApp {

    private static final Logger LOG = Logger.getLogger(MicroserviceApp.class.getName());

    /** Classpath folder for static assets (HTML/CSS/JS). */
    private static final String FRONTEND_RESOURCE_DIR = "frontend/";

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(getConfig("PORT", "9001"));
        String discoveryUrl = getConfig("DISCOVERY_URL", "http://localhost:8500");
        String serviceName = getConfig("SERVICE_NAME", "system-dashboard");
        String host = getConfig("SERVICE_HOST", "localhost");
        String description = getConfig("SERVICE_DESCRIPTION", "System Dashboard Microservice");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("testman", "true");
        metadata.put("uiPath", "/");
        metadata.put("description", description);

        DiscoveryClient discovery = new DiscoveryClient(discoveryUrl, serviceName, host, port, metadata);
        String instanceId = discovery.register();

        if (instanceId == null) {
            LOG.severe("Failed to register with discovery service. Starting without registration.");
            instanceId = "unregistered-" + port;
        }

        ApiHandler apiHandler = new ApiHandler(instanceId, port);
        StaticFileHandler staticHandler = new StaticFileHandler(FRONTEND_RESOURCE_DIR);
        RootDispatcher root = new RootDispatcher(apiHandler, staticHandler);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", root);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        LOG.info("Microservice '" + serviceName + "' on port " + port
                + " (instanceId=" + instanceId + ") — backend /api/*, frontend classpath:" + FRONTEND_RESOURCE_DIR);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down microservice...");
            server.stop(2);
            discovery.shutdown();
        }));
    }

    private static String getConfig(String envKey, String defaultValue) {
        String env = System.getenv(envKey);
        return (env != null && !env.isEmpty()) ? env : defaultValue;
    }
}
