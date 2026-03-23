# Discovery Service

A lightweight service discovery platform written in Java 1.8 with a single dependency (Gson). Microservices register themselves over HTTP, TCP, or UDP. A built-in reverse proxy serves registered UIs through a single origin, enabling microfrontend injection into host applications like TestMan.

## Monorepo layout

Everything lives in **one repository** (this folder):

| Directory | Purpose |
|---|---|
| *(repo root)* | **Discovery server** — `pom.xml`, `src/`, `config/` |
| `example-microservice/` | Registers over **HTTP**; dashboard UI + JSON `/api/status` (paths built to work behind `/proxy/{id}/` with or without a trailing slash) |
| `example-tcp-client/` | Registers over **TCP**; small UI on port 9101 |
| `example-udp-client/` | Registers over **UDP**; small UI on port 9102 |
| `example-testman/` | Vite + React **TestMan** host (`testman=true` services as tabs) |
| `scripts/build-all.sh` | Builds all Java JARs + TestMan |
| `docs/GITHUB.md` | How to **push to a new GitHub repo** |

## Quick Start

**Prerequisites:** Java 8+, Maven, Node.js 18+

```bash
cd /path/to/this-repo

# Build everything (or: mvn package in each Java module + npm in example-testman)
chmod +x scripts/build-all.sh && ./scripts/build-all.sh

# Terminal 1 — discovery
java -jar target/discovery-service-1.0.0.jar --config config/local/node-1.json

# Terminal 2 — HTTP-registered microservices (two instances)
cd example-microservice && PORT=9001 java -jar target/example-microservice-1.0.0.jar
# Terminal 3
cd example-microservice && PORT=9002 java -jar target/example-microservice-1.0.0.jar

# Terminal 4 — TCP demo (uses discovery TCP port from config, default 8501)
cd example-tcp-client && java -jar target/example-tcp-client-1.0.0.jar

# Terminal 5 — UDP demo (default discovery UDP 8502)
cd example-udp-client && java -jar target/example-udp-client-1.0.0.jar

# Terminal 6 — TestMan
cd example-testman && npm install && npm run dev
```

Open `http://localhost:3000`. You should see tabs for **system-dashboard** (2 instances), **tcp-demo-service**, and **udp-demo-service**, each with a proxy iframe. Use **Get info from service** to hit `./api/test-info` through the proxy.

Optional second discovery node:  
`java -jar target/discovery-service-1.0.0.jar --config config/local/node-2.json`

### Push to GitHub

See **[docs/GITHUB.md](docs/GITHUB.md)** (`git init`, `git remote add`, `git push`, or `gh repo create`).

---

## How It Works

### Registration Flow

A microservice registers by POSTing its identity and metadata to the discovery service:

```
POST http://localhost:8500/api/register
Content-Type: application/json

{
  "name": "system-dashboard",
  "host": "192.168.1.10",
  "port": 9001,
  "protocol": "http",
  "metadata": {
    "testman": "true",
    "uiPath": "/",
    "description": "System Dashboard"
  }
}
```

The discovery service assigns a unique `instanceId` (e.g. `system-dashboard-a1b2c3d4`) and returns it. Multiple instances of the same `name` can coexist -- each gets its own `instanceId`.

The microservice then sends heartbeats every 30 seconds. If heartbeats stop, the discovery service evicts the instance after 90 seconds (configurable).

### UI Injection via Reverse Proxy

This is the core mechanism that makes microfrontend injection possible without the host app needing to know the microservice's actual address.

**The proxy path format:**

```
http://discovery:8500/proxy/{instanceId}/{path}
```

When TestMan (or any consumer) requests:

```
GET http://localhost:8500/proxy/system-dashboard-a1b2c3d4/
```

The discovery service:

1. Looks up `system-dashboard-a1b2c3d4` in the registry
2. Finds it at `http://192.168.1.10:9001`
3. Forwards the request to `http://192.168.1.10:9001/`
4. Streams the response (HTML, CSS, JS, images) back to the caller with correct content types

This means TestMan never contacts the microservice directly. It only talks to the discovery service, which proxies everything -- HTML pages, JavaScript files, CSS stylesheets, API calls, and any other assets. The microservice's UI is "injected" into TestMan through an iframe whose `src` points at the proxy URL.

**Proxy flow for a full page load:**

```
TestMan iframe
  |
  |-- GET /proxy/{id}/              --> discovery proxies --> microservice returns index.html
  |-- GET /proxy/{id}/style.css     --> discovery proxies --> microservice returns style.css
  |-- GET /proxy/{id}/app.js        --> discovery proxies --> microservice returns app.js
  |-- GET /proxy/{id}/api/status    --> discovery proxies --> microservice returns JSON
```

Every sub-request (scripts, stylesheets, API calls) from the iframe goes through the proxy because the iframe's origin is the discovery service URL. Relative paths like `href="style.css"` resolve to `/proxy/{id}/style.css` automatically.

### API Endpoint Injection

The same proxy handles API calls. The example microservice serves a `/api/status` endpoint. When its UI (running inside TestMan's iframe) calls:

```javascript
fetch(window.location.origin + '/api/status')
```

This resolves to `http://localhost:8500/proxy/{id}/api/status`, which the discovery service proxies to `http://microservice-host:9001/api/status`. The response (JSON) is returned through the proxy with CORS headers.

This means a microservice's backend API is also injected -- the frontend running inside the iframe can call its own API without knowing its actual host or port. It just uses relative URLs and the proxy handles routing.

---

## Testing the End-to-End Flow

### Step 1: Verify Registration

After starting the discovery service and microservice instances, confirm they registered:

```bash
curl http://localhost:8500/api/services?testman=true
```

Expected response -- instances grouped by service name:

```json
{
  "groups": [
    {
      "name": "system-dashboard",
      "instanceCount": 2,
      "instances": [
        {
          "instanceId": "system-dashboard-a1b2c3d4",
          "host": "localhost",
          "port": 9001,
          "metadata": { "testman": "true", "uiPath": "/" }
        },
        {
          "instanceId": "system-dashboard-e5f6g7h8",
          "host": "localhost",
          "port": 9002,
          "metadata": { "testman": "true", "uiPath": "/" }
        }
      ]
    }
  ]
}
```

### Step 2: Test UI Injection Through the Proxy

Fetch the microservice's HTML through the proxy -- this is exactly what TestMan's iframe does:

```bash
# Fetch the UI HTML through the proxy
curl http://localhost:8500/proxy/system-dashboard-a1b2c3d4/

# Fetch a static asset through the proxy
curl http://localhost:8500/proxy/system-dashboard-a1b2c3d4/style.css

# Fetch JavaScript through the proxy
curl http://localhost:8500/proxy/system-dashboard-a1b2c3d4/app.js
```

Each of these should return the content from the microservice running on port 9001, served through the discovery service on port 8500. The content types are preserved (`text/html`, `text/css`, `application/javascript`).

### Step 3: Test API Endpoint Injection Through the Proxy

The microservice's API is also available through the proxy. This is how the UI running inside the iframe calls its backend:

```bash
# Hit the microservice's API through the proxy
curl http://localhost:8500/proxy/system-dashboard-a1b2c3d4/api/status
```

Expected response -- note the instanceId and port confirm which instance handled the request:

```json
{
  "instanceId": "system-dashboard-a1b2c3d4",
  "port": 9001,
  "uptimeSeconds": 120,
  "uptimeFormatted": "2m 0s",
  "javaVersion": "1.8.0_392",
  "availableProcessors": 8,
  "maxMemoryMB": 4096,
  "freeMemoryMB": 200,
  "pid": "12345"
}
```

Now do the same for the second instance to verify routing is correct:

```bash
curl http://localhost:8500/proxy/system-dashboard-e5f6g7h8/api/status
```

This returns different values for `instanceId`, `port`, and `pid`, proving each proxy path routes to the correct backend instance.

### Step 4: Test Dynamic Discovery in TestMan

1. Open `http://localhost:3000` -- TestMan shows a "System Dashboard" tab with instance count "2"
2. The instance dropdown lists both instances with their host:port
3. Selecting an instance reloads the iframe, showing that instance's data (different PID, port, uptime)
4. Kill one microservice instance -- after 90 seconds, TestMan's dropdown updates to show only the surviving instance
5. Start a new instance on port 9003 -- within 5 seconds, TestMan's dropdown adds it

**TestMan `HTTP 404` with body `{"error":"Not found"}`** — Discovery serves the JSON API under `/api/*` and the reverse proxy under `/proxy/*`. If `VITE_DISCOVERY_URL` is set to `http://localhost:8500/api`, the app will call `http://localhost:8500/api/proxy/...`, which is **not** the proxy (it hits the API router and returns 404). Use the **base URL only**: `http://localhost:8500` (no `/api`). The TestMan client also strips a mistaken `/api` suffix automatically.

**Example microservice `HTTP 404` with `{"error":"Not found"}` on `/api/*`** — Fixed in the example app by (1) normalizing paths in the HTTP dispatcher so requests like `//api/status` still route to the JSON handler, (2) treating **HEAD** like **GET** for `/api/status` and `/api/test-info`, and (3) resolving API and static asset URLs from the current pathname so a proxy URL **without** a trailing slash (e.g. `.../proxy/{id}`) still maps to `.../proxy/{id}/api/status` instead of `.../proxy/api/status`.

### Step 5: Test Heartbeat Eviction

```bash
# Register a temporary service manually
curl -X POST http://localhost:8500/api/register \
  -H "Content-Type: application/json" \
  -d '{"name":"temp-service","host":"localhost","port":9999,"metadata":{"testman":"true"}}'

# Verify it appears
curl http://localhost:8500/api/services?testman=true

# Wait 90+ seconds without sending heartbeats, then check again
sleep 95
curl http://localhost:8500/api/services?testman=true
# temp-service is gone -- evicted by the heartbeat monitor
```

---

## Discovery API Reference

All endpoints are served on the configured HTTP port (default 8500).

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/register` | Register a service instance. Body: `{name, host, port, protocol?, metadata?}`. Returns `{instanceId}`. |
| `DELETE` | `/api/deregister/{instanceId}` | Remove a specific instance. |
| `POST` | `/api/heartbeat/{instanceId}` | Refresh heartbeat timestamp. Send every 30s. |
| `GET` | `/api/services` | List all services, grouped by name. |
| `GET` | `/api/services?testman=true` | Filter by metadata key/value. |
| `GET` | `/api/services?name=system-dashboard` | Filter by service name. |
| `GET` | `/api/instances/{instanceId}` | Get a single instance. |
| `GET` | `/api/health` | Health check. Returns `{status, nodeId, environment, registeredInstances, peers, timestamp}`. |
| `GET` | `/proxy/{instanceId}/**` | Reverse proxy to the instance. Forwards all sub-paths, headers, and body. |
| `POST` | `/api/webhooks` | Add a webhook subscriber. Body: `{url}`. |
| `DELETE` | `/api/webhooks` | Remove a webhook subscriber. Body: `{url}`. |
| `GET` | `/api/webhooks` | List webhook subscribers. |
| `GET` | `/api/sync/state` | Returns this node's full registry state for peer sync. |
| `POST` | `/api/sync/event` | Receives a change event from a peer. Body: `{sourceNodeId, eventType, instance}`. |

**TCP** (configured `tcp.port`) and **UDP** (`udp.port`) share the same JSON protocol:

| Message | JSON body | Response |
|---|---|---|
| Register (legacy) | Same object as `POST /api/register` — no `op` field | `{instanceId, name}` or `{error}` |
| Register (explicit) | `{"op":"register", "name", "host", "port", ...}` | same |
| Heartbeat | `{"op":"heartbeat","instanceId":"..."}` | `{ok:true}` or `{error}` |
| Deregister | `{"op":"deregister","instanceId":"..."}` | `{ok:true}` or `{error}` |

**TCP:** one request per connection — send JSON, half-close output (`shutdownOutput`), read response until EOF. **UDP:** one datagram per request; response is one datagram (keep payloads small).

---

## Configuration

Each discovery service instance is configured via a JSON file. Config files are organized by environment:

```
config/
  local/
    node-1.json        # First local instance (HTTP :8500)
    node-2.json        # Second local instance (HTTP :8600)
  dev/
    node-1.json
  production/
    node-1.json
    node-2.json
    node-3.json
```

Pass the config path at startup via `--config` or the `DISCOVERY_CONFIG` env var:

```bash
java -jar target/discovery-service-1.0.0.jar --config config/local/node-1.json
```

### Config File Reference

```json
{
  "nodeId": "node-1",
  "environment": "local",
  "http": { "port": 8500, "threads": 20 },
  "tcp": { "port": 8501 },
  "udp": { "port": 8502 },
  "heartbeat": { "ttlMs": 90000, "checkIntervalMs": 30000 },
  "data": { "dir": "data/local/node-1" },
  "sync": { "intervalMs": 5000, "timeoutMs": 5000 },
  "webhooks": [],
  "peers": [ "http://localhost:8600" ]
}
```

| Field | Default | Description |
|---|---|---|
| `nodeId` | `"node-1"` | Unique identifier for this discovery node |
| `environment` | `"local"` | Environment name (organizational, shown in health endpoint) |
| `http.port` | `8500` | HTTP API + proxy port |
| `http.threads` | `20` | HTTP thread pool size |
| `tcp.port` | `8501` | TCP registration port |
| `udp.port` | `8502` | UDP registration port |
| `heartbeat.ttlMs` | `90000` | Time (ms) before evicting a silent instance |
| `heartbeat.checkIntervalMs` | `30000` | How often the monitor checks for stale instances |
| `data.dir` | `"data"` | Directory for this node's `registry.json` and `webhooks.json` |
| `sync.intervalMs` | `5000` | How often to pull full state from peers |
| `sync.timeoutMs` | `5000` | HTTP timeout for peer sync requests |
| `webhooks` | `[]` | Webhook URLs to pre-register |
| `peers` | `[]` | HTTP base URLs of peer discovery nodes |

Each node should have its own `data.dir` so their persisted state is isolated (e.g. `data/local/node-1`, `data/local/node-2`).

The example microservice is configured with:

| Env Variable | Default | Description |
|---|---|---|
| `PORT` | `9001` | HTTP port for this instance |
| `DISCOVERY_URL` | `http://localhost:8500` | Discovery service address |
| `SERVICE_NAME` | `system-dashboard` | Logical service name (shared across instances) |
| `SERVICE_HOST` | `localhost` | Hostname to register with discovery |
| `SERVICE_DESCRIPTION` | `System Dashboard Microservice` | Human-readable description |

---

## Webhooks (Nginx Sidecar Readiness)

The discovery service fires webhook events when the registry changes. This is designed for an nginx sidecar that regenerates upstream config on topology changes.

**Register a webhook subscriber:**

```bash
curl -X POST http://localhost:8500/api/webhooks \
  -H "Content-Type: application/json" \
  -d '{"url":"http://localhost:9090/on-change"}'
```

**Webhook event payload (sent on every REGISTER, DEREGISTER, EVICT):**

```json
{
  "event": "REGISTER",
  "timestamp": 1711234567890,
  "instanceId": "system-dashboard-a1b2c3d4",
  "serviceName": "system-dashboard",
  "instance": {
    "instanceId": "system-dashboard-a1b2c3d4",
    "name": "system-dashboard",
    "host": "192.168.1.10",
    "port": 9001,
    "protocol": "http",
    "metadata": { "testman": "true", "uiPath": "/" }
  },
  "allInstancesForService": [
    { "instanceId": "system-dashboard-a1b2c3d4", "host": "192.168.1.10", "port": 9001 },
    { "instanceId": "system-dashboard-e5f6g7h8", "host": "192.168.1.11", "port": 9002 }
  ]
}
```

The `allInstancesForService` array contains every healthy instance of the affected service name. An nginx sidecar can use this to rebuild the `upstream` block directly without polling.

---

## Clustered Discovery (Peer Sync)

Discovery service nodes running in the same environment can sync their registries so that a service registered on any node is visible from all nodes.

### How Sync Works

Each node uses two mechanisms to stay in sync with its peers:

1. **Push on change** — When a local event occurs (REGISTER, DEREGISTER, EVICT), the node immediately POSTs the event to each peer's `/api/sync/event` endpoint. This provides near-instant propagation.

2. **Periodic pull** — Every `sync.intervalMs` (default 5s), the node GETs `/api/sync/state` from each peer and merges any missing or stale instances using last-write-wins on the `lastHeartbeat` timestamp. This catches anything missed by push (e.g. if a peer was briefly unreachable).

Sync-originated changes fire as `SYNC` events internally so they are not re-propagated to other peers (no echo loops). Webhook subscribers still receive `SYNC` events.

### Conflict Resolution

When two nodes have the same instance, the one with the **newer `lastHeartbeat`** wins. Heartbeats are synced through periodic pull, so as long as `sync.intervalMs` is well below `heartbeat.ttlMs`, heartbeats stay fresh across the cluster.

If a node goes down, its instances will eventually be evicted from surviving nodes when heartbeats stop being synced and the TTL expires. This is self-healing by design.

### Example: 2-Node Local Cluster

```bash
# Terminal 1 — node-1 on ports 8500/8501/8502
java -jar target/discovery-service-1.0.0.jar --config config/local/node-1.json

# Terminal 2 — node-2 on ports 8600/8601/8602
java -jar target/discovery-service-1.0.0.jar --config config/local/node-2.json
```

Register a service on node-1, then query node-2:

```bash
# Register on node-1
curl -X POST http://localhost:8500/api/register \
  -H "Content-Type: application/json" \
  -d '{"name":"my-service","host":"localhost","port":9001}'

# Within seconds, query node-2 — the service appears
curl http://localhost:8600/api/services
```

### Production Cluster

For a production 3-node cluster, create config files under `config/production/`:

```
config/production/
  node-1.json    # HTTP :8500, peers: [node-2-url, node-3-url]
  node-2.json    # HTTP :8500, peers: [node-1-url, node-3-url]
  node-3.json    # HTTP :8500, peers: [node-1-url, node-2-url]
```

Each node lists the other nodes' HTTP base URLs in its `peers` array. All nodes in the same environment share the same `environment` field value.

## Multi-Instance and Distributed Deployment

The registry distinguishes between **service name** (logical type, e.g. `system-dashboard`) and **instance ID** (unique per running process). This allows:

- Running N instances of the same service across different servers
- Each instance registering with its own host and port
- Consumers querying by service name and receiving all instances
- TestMan showing one tab per service type with a dropdown to pick an instance

**Example: 3 instances across 2 servers**

```bash
# Server A (192.168.1.10)
DISCOVERY_URL=http://discovery:8500 SERVICE_HOST=192.168.1.10 PORT=9001 java -jar example-microservice-1.0.0.jar
DISCOVERY_URL=http://discovery:8500 SERVICE_HOST=192.168.1.10 PORT=9002 java -jar example-microservice-1.0.0.jar

# Server B (192.168.1.11)
DISCOVERY_URL=http://discovery:8500 SERVICE_HOST=192.168.1.11 PORT=9001 java -jar example-microservice-1.0.0.jar
```

All three register as `system-dashboard` with their respective host:port. The discovery service groups them under one name, and TestMan shows a single tab with a 3-item dropdown.

---

## Building Your Own Microservice

To create a new microservice that integrates with TestMan:

1. **Serve a UI** on your HTTP port (static files, SPA, or server-rendered)
2. **Use relative paths** in your HTML (`href="style.css"` not `href="/style.css"`) so assets resolve correctly through the proxy
3. **Register with discovery** on startup:

```bash
curl -X POST http://discovery:8500/api/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-service",
    "host": "'$(hostname -I | awk "{print \$1}")'",
    "port": 8080,
    "metadata": {
      "testman": "true",
      "uiPath": "/",
      "description": "My Custom Service"
    }
  }'
```

4. **Send heartbeats** every 30 seconds:

```bash
curl -X POST http://discovery:8500/api/heartbeat/{instanceId}
```

5. **Deregister** on shutdown:

```bash
curl -X DELETE http://discovery:8500/api/deregister/{instanceId}
```

Or use the `DiscoveryClient.java` from the example microservice as a drop-in client that handles all of this automatically.

---

## Architecture

```
                     +---------------------------+          +---------------------------+
                     |   Discovery Node 1        |          |   Discovery Node 2        |
                     |   (config/local/node-1)    |  sync    |   (config/local/node-2)    |
                     |   :8500 HTTP               |<-------->|   :8600 HTTP               |
                     |   :8501 TCP                |  push/   |   :8601 TCP                |
                     |   :8502 UDP                |  pull    |   :8602 UDP                |
                     |                            |          |                            |
 Microservices       |  +----------------------+  |          |  +----------------------+  |
 register/heartbeat->|  | ServiceRegistry      |  |          |  | ServiceRegistry      |  |
                     |  | (ConcurrentMap)       |  |          |  | (ConcurrentMap)       |  |
                     |  +----------------------+  |          |  +----------------------+  |
                     |         |          |        |          |         |                  |
                     |  +------+   +------+-----+  |          |  +------+-----+            |
                     |  | File |   | PeerSync   |  |          |  | PeerSync   |            |
                     |  | data/|   | Manager    |  |          |  | Manager    |            |
                     |  +------+   +------------+  |          |  +------------+            |
                     |         |                   |          |                            |
 GET /proxy/{id}/ -->|  +------------+             |          |                            |
                     |  | ProxyHandler|-->microservice        |                            |
                     |  +------------+             |          |                            |
                     |         |                   |          |                            |
                     |  +---------------+          |          |                            |
                     |  | WebhookNotifier|-->nginx  |          |                            |
                     |  +---------------+          |          |                            |
                     +---------------------------+          +---------------------------+
```
