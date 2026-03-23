# Example microservice

Small demo service that registers with the discovery server and serves:

| Layer | What | Where |
|--------|------|--------|
| **Backend** | JSON API (`HttpServer` + `ApiHandler`) | `src/main/java/.../ApiHandler.java` |
| **Frontend** | Static HTML/CSS/JS | `src/main/resources/frontend/` |

One JVM process serves both: `RootDispatcher` sends `/api/*` to the backend and everything else to `StaticFileHandler` (classpath `frontend/`).

## Why relative API URLs?

When ProcMan embeds the UI in an iframe, the page URL is like:

`http://localhost:8500/proxy/{instanceId}/`

The browser origin is the **discovery** host. Using `http://localhost:8500/api/status` would hit the **discovery** API (wrong). The frontend must call **`./api/status`** so the request goes to:

`http://localhost:8500/proxy/{instanceId}/api/status`

which the discovery proxy forwards to the microservice.

## API

- `GET /api/status` — full status JSON for the dashboard
- `GET /api/procman-info` — small JSON for ProcMan’s “Get info from service” button
- `OPTIONS /api/*` — CORS preflight

## Run

```bash
mvn package -q
PORT=9001 java -jar target/example-microservice-1.0.0.jar
```

Set `DISCOVERY_URL` if discovery is not `http://localhost:8500`.
