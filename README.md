# Courier Tracking

A Spring Boot REST service that ingests streaming courier geolocations `(time, courier, lat, lng)`,
logs and stores **store entrances** (within 100 m of a Migros store, with a 1 minute re-entry
cooldown) and answers **total travel distance** per courier.

Java 17 · Spring Boot 3.3 · Spring Data JPA · Flyway · H2 · springdoc-openapi

## Run

```bash
./mvnw spring-boot:run          # or: ./mvnw package && java -jar target/courier-tracking-1.0.0.jar
./demo.sh                       # guided demo (uses the running app, or starts a temporary one)
./mvnw test                     # unit, API and concurrency tests
```

Docker: `docker build -t courier-tracking . && docker run -p 8080:8080 courier-tracking`

UI demo (map): http://localhost:8080/ · Swagger UI: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/actuator/health

Port 8080 busy? Use `SERVER_PORT=8089 ./mvnw spring-boot:run` and `./demo.sh http://localhost:8089`.

Data is kept in a file-based H2 database under `./data`; delete that folder to start fresh.

### UI demo

The app serves a small single-page demo at `http://localhost:8080/` (`src/main/resources/static/index.html`). It talks to the same REST API (`/api/v1`) from the browser and shows the stores and couriers on a map. Leaflet and the OpenStreetMap tiles are loaded from the internet, so the map needs a connection.

### Scripts

Only need `bash` and `curl`.

```bash
./scripts/start.sh [--fresh] [--build] [--port N]   # start in background, wait for health
./scripts/demo.sh [--auto] [base-url]               # guided walkthrough (same as ./demo.sh)
./scripts/stop.sh
./scripts/curl-examples.sh [base-url]               # plain curl call for every endpoint, copy-pasteable
```

`demo.sh` prints the latitude/longitude of every ping it sends. `curl-examples.sh` shows each curl command before running it, so you can copy single calls.

## API

| Method | Path                                           | Description                                                                                         |
| ------ | ---------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `POST` | `/api/v1/locations`                            | Ingest one location. Body: `{"courier":"c1","time":"2026-01-01T10:00:00Z","lat":40.99,"lng":29.12}` |
| `GET`  | `/api/v1/couriers/{courierId}/total-distance`  | `{"courierId","totalDistanceMeters","totalDistanceKilometers"}` (404 if unknown)                    |
| `GET`  | `/api/v1/couriers/{courierId}/store-entrances` | Counted entrances in chronological order                                                            |
| `GET`  | `/api/v1/stores`                               | Loaded stores                                                                                       |

`POST /locations` returns `{"status":"PROCESSED|IGNORED_STALE","distanceAddedMeters":..,"enteredStores":[..]}`.
Errors are RFC 9457 `application/problem+json`.

```bash
curl -X POST localhost:8080/api/v1/locations -H 'Content-Type: application/json' \
  -d '{"courier":"c1","time":"2026-01-01T10:00:00Z","lat":40.9925,"lng":29.1244229}'
curl localhost:8080/api/v1/couriers/c1/total-distance
```

## Requirement interpretation

The case leaves several points open. Decisions taken (all tunables are in `application.yml`):

| Ambiguity                                   | Decision                                                                                                                                                                                                                 |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| What is an "entrance"?                      | An **outside → inside transition** of a store's 100 m circle (`distance <= 100 m`). Pings while staying inside do not create new entrances. The first ever ping of a courier, if inside a store, counts.                 |
| "Re-entries over 1 minute should not count" | Read as _within_ 1 minute: a re-entry is ignored if the courier's **last counted entrance** to the _same_ store was less than 60 s earlier (event time). Exactly 60 s counts. Cooldown is per courier **and** per store. |
| Which clock?                                | The **event time** sent by the device, not server receive time, so delayed delivery does not distort cooldowns or order.                                                                                                 |
| Out-of-order / duplicate pings              | A ping not strictly newer than the courier's last processed ping is **ignored** (`IGNORED_STALE`). Makes ingestion idempotent under client retries; late points are dropped rather than rewriting history.               |
| Future timestamps                           | Rejected if more than `max-clock-skew` (5 min) ahead of the server, otherwise one bad ping would make all real ones look stale.                                                                                          |
| Distance                                    | Sum of great-circle (Haversine) distances between consecutive accepted pings, in meters. No GPS-jitter filtering (a parked courier accrues noise);                                                                       |
| Unknown courier                             | `404` rather than `0.0`, to distinguish "never seen" from "not moved".                                                                                                                                                   |
| `time` format                               | ISO-8601 instant, e.g. `2026-01-01T10:00:00Z`.                                                                                                                                                                           |

## Architecture

```
api/        REST controllers, request/response DTOs, error mapping (no business logic)
courier/    Ingestion (write path) and query (read path) services, JPA aggregate + repositories
store/      Immutable store registry loaded once from stores.json
geo/        GeoPoint value object and distance strategy
event/      Store entrance domain event and its observers
config/     Typed configuration and Clock
```

Write path: `Controller → LocationIngestionService (validation, retry) → LocationProcessor (@Transactional) → repositories` and publishes `StoreEntranceEvent`.

## Design patterns

1. **Strategy** – `DistanceCalculator` with `HaversineDistanceCalculator`. Distance math is swappable
   (e.g. Vincenty, or a cheaper equirectangular approximation) without touching business code.
2. **Observer** – `StoreEntranceEvent` published via Spring's `ApplicationEventPublisher`;
   `StoreEntranceLogger` is a `@TransactionalEventListener` that logs **after commit**. New reactions
   (notifications, Kafka publisher, metrics) are new listeners, ingestion stays unchanged.

## Scaling notes

The current design is intentionally simple: one instance, one database, per-courier row lock. It is
correct and easy to reason about, but it is not the end state for real streaming volumes. Where it
would break first and what would change:

| Concern | Today | At scale |
| ------- | ----- | -------- |
| Ingestion transport | Synchronous `POST /locations`, one ping per request | Consume from a partitioned log (e.g. Kafka) keyed by `courierId`, with batch ingestion; keep the REST endpoint as a thin producer/adapter |
| Per-courier ordering and concurrency | `PESSIMISTIC_WRITE` lock on the courier row, retry on first-insert race | Partitioning by `courierId` gives a single consumer per courier, so locks and the insert-race retry disappear; keep `@Version` as a safety net |
| Hot-path DB access | Each ping reads/updates the `courier` row; each candidate entrance queries the last entrance | Keep courier state (last location, last entrance per store) in memory or Redis in the partition owner, and write to the DB asynchronously/in batches |
| Store lookup | Linear scan over all stores (O(stores)) | Spatial index (geohash/H3 grid or R-tree) to fetch only nearby stores; trivial for 5 stores, needed for thousands |
| Write amplification | Update of `courier` on every ping | Coalesce updates (write total distance every N pings or T seconds), or append-only pings plus a periodic aggregate |
| Raw data | Only last location and running total are stored; late pings are dropped | Append raw pings to a log/time-series store: enables audit, recomputation if the distance algorithm changes, and reprocessing late data |
| Read path | `getTotalTravelDistance` is O(1) on the courier row | Already cheap; add read replicas or a cache if query volume grows |
| Database | File-based H2 | PostgreSQL (Flyway migrations are already portable); integration tests via Testcontainers |
| Horizontal scaling | Single instance (the lock lives in one DB, so multiple instances are safe but contend) | Stateless API instances plus partitioned consumers; scale consumers up to the partition count |
| Observability | Health endpoint and logs | Micrometer metrics (ingest rate, stale ratio, lock timeouts, entrance count), tracing, alerts on consumer lag |

Accuracy limits that also matter at scale: entrances are detected from pings only, so a fast courier can
cross the 100 m circle between two pings undetected (segment-circle intersection would fix this), and there
is no GPS-jitter or impossible-speed filtering (a speed/min-distance threshold would be the first addition).
