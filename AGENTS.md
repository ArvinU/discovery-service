# AGENTS.md

## Cursor Cloud specific instructions

### Product overview

This is a **Discovery Service** monorepo: a Java service-discovery server with reverse proxy, example microservice clients (HTTP/TCP/UDP), and a Vite + React **TestMan** host UI. See `README.md` for architecture and API details.

### Prerequisites (system-level)

- **Java 8+** (Java 21 works; code targets 1.8)
- **Maven** (`mvn`) — not bundled in the base image; install via `apt` if missing
- **Node.js 18+** and npm

### Build

```bash
chmod +x scripts/build-all.sh && ./scripts/build-all.sh
```

Builds all Maven JARs and runs `npm install` + `npm run build` for `example-testman`.

### Running services (minimum E2E)

Start in separate terminals (or tmux sessions). **Start discovery first**, then clients — microservices register on startup and will fail if discovery is not yet listening.

| Service | Command | Port |
|---------|---------|------|
| Discovery | `java -jar target/discovery-service-1.0.0.jar --config config/local/node-1.json` | 8500 (HTTP), 8501 (TCP), 8502 (UDP) |
| HTTP microservice | `cd example-microservice && PORT=9001 java -jar target/example-microservice-1.0.0.jar` | 9001 |
| TestMan (dev) | `cd example-testman && npm run dev` | 3000 |

Optional: second microservice on `PORT=9002`, `example-tcp-client` (:9101), `example-udp-client` (:9102), second discovery node via `config/local/node-2.json` (:8600).

### Lint / test

- **No dedicated lint or test suite** in the repo.
- Closest check for TestMan: `cd example-testman && npm run build` (runs `tsc -b` + Vite build).
- E2E validation is manual: `curl http://localhost:8500/api/health` and open `http://localhost:3000`.

### Gotchas

- If a microservice logs `Failed to register with discovery service`, restart it after discovery is up (registration happens once at startup).
- `VITE_DISCOVERY_URL` must be the **base URL only** (`http://localhost:8500`), not `.../api`.
- Discovery persists registry state under `data/`; safe to delete for a clean slate.
