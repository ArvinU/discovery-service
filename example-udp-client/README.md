# example-udp-client

Registers with **discovery’s UDP port** (one datagram of JSON), then keeps the instance alive with **HTTP** heartbeats.

Serves a tiny **HTTP + static frontend** on `PORT` (default **9102**).

## Run

```bash
mvn -q package

DISCOVERY_URL=http://localhost:8500 \
DISCOVERY_UDP_HOST=localhost \
DISCOVERY_UDP_PORT=8502 \
PORT=9102 \
java -jar target/example-udp-client-1.0.0.jar
```

Match `DISCOVERY_UDP_PORT` to your discovery config (`udp.port`, often `8502`).
