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

Swagger UI: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/actuator/health

Port 8080 busy? Use `SERVER_PORT=8089 ./mvnw spring-boot:run` and `./demo.sh http://localhost:8089`.

Data is kept in a file-based H2 database under `./data`; delete that folder to start fresh.

### Scripts

Only need `bash` and `curl`.

```bash
./scripts/start.sh [--fresh] [--build] [--port N]   # start in background, wait for health
./scripts/demo.sh [--auto] [base-url]               # guided walkthrough (same as ./demo.sh)
./scripts/stop.sh
```

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
