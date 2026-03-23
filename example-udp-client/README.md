# example-udp-client

Registers with **discovery’s UDP port** (one datagram of JSON), then heartbeats with datagrams `{"op":"heartbeat","instanceId":"..."}` and deregisters with `{"op":"deregister","instanceId":"..."}` (interval: `DISCOVERY_HEARTBEAT_INTERVAL_SEC`, default 30).

Serves a tiny **HTTP + static frontend** on `PORT` (default **9102**).

## Run

```bash
mvn -q package

DISCOVERY_UDP_HOST=localhost \
DISCOVERY_UDP_PORT=8502 \
DISCOVERY_HEARTBEAT_INTERVAL_SEC=30 \
PORT=9102 \
java -jar target/example-udp-client-1.0.0.jar
```

Match `DISCOVERY_UDP_PORT` to your discovery config (`udp.port`, often `8502`).
