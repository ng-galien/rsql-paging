# k6 Load Tests

Performance tests for the rsql-paging demo API using [k6](https://k6.io/).

## Prerequisites

- [k6](https://k6.io/docs/get-started/installation/) installed
- Demo application running (`mvn spring-boot:run -pl rsql-paging-demo`)
- PostgreSQL running (`docker compose up -d`)

## Usage

```bash
# Run with default settings (http://localhost:8080)
k6 run k6/paging-test.js

# Run against a different host
k6 run k6/paging-test.js -e BASE_URL=http://localhost:9090
```

## Test scenarios

The script defines three sequential scenarios:

| Scenario | Executor | VUs | Duration | Start |
|----------|----------|-----|----------|-------|
| **smoke** | constant-vus | 1 | 10s | 0s |
| **load** | ramping-vus | 0 → 10 → 0 | 40s | 15s |
| **stress** | ramping-vus | 0 → 20 → 50 → 0 | 35s | 60s |

### Smoke test

Functional validation with a single virtual user:
- Basic pagination (page size, first/last flags, totalElements)
- Page navigation (sequential pages, no overlap, beyond-range)
- RSQL filters (equality, comparison, relation traversal, AND)
- Sort orders (asc, desc, fallback to ID)
- Error handling (invalid RSQL, bad sort property, negative page, zero size)

### Load test

Concurrent users with randomized parameters:
- Random RSQL filters (`price>50`, `category.name==Electronics`, combined filters, etc.)
- Random sort orders and page sizes
- Random page numbers

### Stress test

Same as load test but with higher concurrency (up to 50 VUs) to find breaking points.

## Thresholds

| Metric | Threshold |
|--------|-----------|
| `http_req_duration` p95 | < 500ms |
| `http_req_duration` p99 | < 1000ms |
| `paging_duration` p95 | < 300ms |
| `errors` rate | < 1% |

## Custom metrics

- `errors` — Rate of failed checks
- `paging_duration` — Trend of API response times (ms)
