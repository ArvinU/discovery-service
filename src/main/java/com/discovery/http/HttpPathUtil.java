package com.discovery.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Normalizes HTTP paths for routing (trailing slashes, duplicate slashes, percent-decoding).
 */
public final class HttpPathUtil {

    private HttpPathUtil() {
    }

    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        String path = rawPath;
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // keep raw path
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
}
