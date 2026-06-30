# Navigation API Performance Report

## Test Target

- API: `POST /api/v1/navigation/routes`
- Scenario: outdoor walking route + indoor destination route
- Script: `performance/k6/navigation-routes.js`
- Base URL: `http://127.0.0.1:8080`
- Date: 2026-06-30

## Baseline

### Smoke Test

Command:

```bash
k6 run -e VUS=1 -e DURATION=10s performance/k6/navigation-routes.js
```

Result:

| Metric | Value |
| --- | ---: |
| Virtual users | 1 |
| Requests | 47 |
| Request rate | 0.67 req/s |
| Failure rate | 0.00% |
| Average response time | 476.50 ms |
| Median response time | 452.15 ms |
| p90 response time | 613.06 ms |
| p95 response time | 629.46 ms |
| Max response time | 646.11 ms |

### Baseline Load Test

Command:

```bash
k6 run -e VUS=10 -e DURATION=1m performance/k6/navigation-routes.js
```

Result:

| Metric | Value |
| --- | ---: |
| Virtual users | 10 |
| Requests | 818 |
| Request rate | 6.81 req/s |
| Failure rate | 0.00% |
| Average response time | 123.74 ms |
| Median response time | 120.83 ms |
| p90 response time | 145.90 ms |
| p95 response time | 157.36 ms |
| Max response time | 315.44 ms |
| Data received | 2.0 MB |
| Data sent | 342 kB |

### Baseline Load Test - 30 VUs

Command:

```bash
k6 run -e VUS=30 -e DURATION=1m performance/k6/navigation-routes.js
```

Result:

| Metric | Value |
| --- | ---: |
| Virtual users | 30 |
| Requests | 2,476 |
| Request rate | 20.63 req/s |
| Failure rate | 0.00% |
| Average response time | 101.77 ms |
| Median response time | 95.44 ms |
| p90 response time | 137.25 ms |
| p95 response time | 155.72 ms |
| Max response time | 350.93 ms |
| Data received | 6.0 MB |
| Data sent | 1.0 MB |

## Indoor-Only Baseline

### Test Scenario

- API: `POST /api/v1/navigation/routes`
- Script: `performance/k6/indoor-navigation-routes.js`
- Route: `구찌(public_id=104)` to `더로우(public_id=125)`
- Building: `신세계백화점 본점 디 에스테이트`
- Purpose: measure backend indoor routing performance without consuming external TMAP route API quota

The script checks that every returned route leg is `INDOOR`, so the test fails if an outdoor/TMAP-backed leg is included.

### Smoke Test

Command:

```bash
k6 run -e VUS=1 -e DURATION=10s performance/k6/indoor-navigation-routes.js
```

Result:

| Metric | Value |
| --- | ---: |
| Virtual users | 1 |
| Requests | 61 |
| Request rate | 0.86 req/s |
| Failure rate | 0.00% |
| Average response time | 159.58 ms |
| Median response time | 158.11 ms |
| p90 response time | 192.07 ms |
| p95 response time | 206.13 ms |
| Max response time | 240.12 ms |
| Indoor-only check | 100.00% passed |

### Before Load Test - 10 VUs

Command:

```bash
k6 run -e VUS=10 -e DURATION=1m performance/k6/indoor-navigation-routes.js
```

Result:

| Metric | Value |
| --- | ---: |
| Virtual users | 10 |
| Requests | 772 |
| Request rate | 6.36 req/s |
| Failure rate | 0.00% |
| Average response time | 186.27 ms |
| Median response time | 195.16 ms |
| p90 response time | 229.35 ms |
| p95 response time | 249.70 ms |
| Max response time | 455.44 ms |
| Indoor-only check | 100.00% passed |
| Data received | 19 MB |
| Data sent | 243 kB |

### Before Load Test - 30 VUs

Command:

```bash
k6 run -e VUS=30 -e DURATION=1m performance/k6/indoor-navigation-routes.js
```

Result:

| Metric | Value |
| --- | ---: |
| Virtual users | 30 |
| Requests | 2,237 |
| Request rate | 18.58 req/s |
| Failure rate | 0.00% |
| Average response time | 218.41 ms |
| Median response time | 222.09 ms |
| p90 response time | 292.81 ms |
| p95 response time | 321.24 ms |
| Max response time | 573.75 ms |
| Indoor-only check | 100.00% passed |
| Data received | 55 MB |
| Data sent | 705 kB |

### Before Load Test - 50 VUs

Command:

```bash
k6 run -e VUS=50 -e DURATION=1m performance/k6/indoor-navigation-routes.js
```

Result:

| Metric | Value |
| --- | ---: |
| Virtual users | 50 |
| Requests | 3,845 |
| Request rate | 32.01 req/s |
| Failure rate | 0.00% |
| Average response time | 177.42 ms |
| Median response time | 169.60 ms |
| p90 response time | 267.42 ms |
| p95 response time | 307.77 ms |
| Max response time | 564.37 ms |
| Indoor-only check | 100.00% passed |
| Data received | 95 MB |
| Data sent | 1.2 MB |

### Before Summary

| Scenario | Requests | Request Rate | Failure Rate | Avg | p95 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Indoor-only 10 VUs | 772 | 6.36 req/s | 0.00% | 186.27 ms | 249.70 ms | 455.44 ms |
| Indoor-only 30 VUs | 2,237 | 18.58 req/s | 0.00% | 218.41 ms | 321.24 ms | 573.75 ms |
| Indoor-only 50 VUs | 3,845 | 32.01 req/s | 0.00% | 177.42 ms | 307.77 ms | 564.37 ms |

## Optimization

### Published Routing Graph Cache

Before optimization, each indoor route request loaded the currently published map version and rebuilt the routing graph by reading nodes, edges, vertical connectors, landmarks, and obstacles from the database.

The optimization adds an in-memory cache for published `RoutingGraph` instances in `MapQueryFacade`.

- Cache key: `mapType + ownerId + mapVersionId`
- Cached value: published `RoutingGraph`
- Still executed per request: source/destination POI resolution and shortest-path calculation
- Avoided on cache hits: repeated graph DB reads and graph object reconstruction
- Invalidation: when a building draft is published, `MapEditorPublishFinalizeService` evicts the building's published routing graph cache
- Stale version handling: if the published `mapVersionId` changes, old cache entries for that map owner are removed

This caches the stable map graph data, not the final route result. Route calculation still runs for every request.

### After Load Test

Restart the backend after applying the optimization, then run the same indoor-only scenarios:

```bash
k6 run -e VUS=10 -e DURATION=1m performance/k6/indoor-navigation-routes.js
k6 run -e VUS=30 -e DURATION=1m performance/k6/indoor-navigation-routes.js
k6 run -e VUS=50 -e DURATION=1m performance/k6/indoor-navigation-routes.js
```

| Scenario | Requests | Request Rate | Failure Rate | Avg | p95 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Indoor-only 10 VUs | 826 | 6.88 req/s | 0.00% | 108.43 ms | 191.27 ms | 1.41 s |
| Indoor-only 30 VUs | 2,506 | 20.86 req/s | 0.00% | 85.13 ms | 131.03 ms | 224.87 ms |
| Indoor-only 50 VUs | 4,149 | 34.55 req/s | 0.00% | 90.51 ms | 130.33 ms | 232.96 ms |

### Before/After Summary

| Scenario | Before p95 | After p95 | p95 Change | Before Avg | After Avg | Avg Change | Before RPS | After RPS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Indoor-only 10 VUs | 249.70 ms | 191.27 ms | -23.4% | 186.27 ms | 108.43 ms | -41.8% | 6.36 | 6.88 |
| Indoor-only 30 VUs | 321.24 ms | 131.03 ms | -59.2% | 218.41 ms | 85.13 ms | -61.0% | 18.58 | 20.86 |
| Indoor-only 50 VUs | 307.77 ms | 130.33 ms | -57.7% | 177.42 ms | 90.51 ms | -49.0% | 32.01 | 34.55 |

After applying published routing graph caching, the indoor-only routing scenario kept a 0.00% failure rate and reduced p95 latency by 23.4% to 59.2% depending on load level.

## Notes

- Opening `/api/v1/navigation/routes` in a browser sends a `GET` request, but this endpoint expects a JSON `POST` request.
- The baseline load test values are from the local run captured for this report.
- The current `navigation-routes.js` scenario sends `routeTypes: ["WALK"]`. This can still call the external TMAP pedestrian route API through the backend, so repeated load tests may consume external API quota.
- Use `indoor-navigation-routes.js` for larger local load tests because it verifies that responses are indoor-only and avoids the external TMAP route API.
- The k6 script ramps users up for 30 seconds, holds the configured `DURATION`, then ramps down for 30 seconds.
