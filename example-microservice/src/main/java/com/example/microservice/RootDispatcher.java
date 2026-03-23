package com.example.microservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class RootDispatcher implements HttpHandler {

    private final HttpHandler backend;
    private final HttpHandler frontend;

    public RootDispatcher(HttpHandler backend, HttpHandler frontend) {
        this.backend = backend;
        this.frontend = frontend;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path != null && path.startsWith("/api")) {
            backend.handle(exchange);
        } else {
            frontend.handle(exchange);
        }
    }
}
