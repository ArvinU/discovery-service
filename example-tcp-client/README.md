# example-tcp-client

Registers with **discovery’s TCP port** (same JSON as `POST /api/register`), then keeps the instance alive with **HTTP** `POST /api/heartbeat/{id}` every 30s.

Serves a tiny **HTTP + static frontend** on `PORT` (default **9101**) so ProcMan can proxy the UI (`procman=true` in metadata).

## Run

```bash
# From repo root, after `./scripts/build-all.sh` or:
mvn -q package

DISCOVERY_URL=http://localhost:8500 \
DISCOVERY_TCP_HOST=localhost \
DISCOVERY_TCP_PORT=8501 \
PORT=9101 \
java -jar target/example-tcp-client-1.0.0.jar
```

Match `DISCOVERY_TCP_PORT` to your discovery config (`tcp.port`, often `8501`).
