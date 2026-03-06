# rsql-paging

[![CI](https://github.com/ng-galien/rsql-paging/actions/workflows/ci.yml/badge.svg)](https://github.com/ng-galien/rsql-paging/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/github/license/ng-galien/rsql-paging)](LICENSE)

Mini-lib Spring Boot 3 / Java 21 implementing an **ID-first** paging strategy with [RSQL](https://github.com/perplexhub/rsql-jpa-specification) filtering.

## The problem

Classic paging with `OFFSET` / `LIMIT` and `COUNT(*)` on queries with complex joins is slow and unstable:
- Deep `OFFSET` scans all preceding rows
- `COUNT(*)` re-executes the full query
- Joins multiply rows and skew the count

## The solution: 3-step paging

1. **ID query** — Lightweight query that fetches only IDs matching the RSQL filter, sorted. No `LIMIT`, no unnecessary `JOIN` — just longs in memory.
2. **In-memory slicing** — `count = ids.size()`. The current page is a `subList(page * size, ...)`. No `COUNT` in the database, no `OFFSET`.
3. **Hydration** — `WHERE id IN (...)` query on the current page's IDs. The caller provides their own hydration function (fetch joins, entity graphs, projections...).

### Trade-off and limit

All IDs are loaded into memory on each request. This is negligible in size (longs), but means the ID query scans the entire filtered result set. For typical volumes, this is significantly faster than a deep `OFFSET` or a separate `COUNT`.

A **hard limit** of **1 million IDs** is applied by default. Beyond that, the query fails with **413 Payload Too Large**. This limit is configurable:

```yaml
rsql:
  paging:
    max-id-count: 500000   # default: 1000000
```

## Quickstart

### Prerequisites

- Java 21+
- Maven
- Docker (for PostgreSQL)

### Run the demo

```bash
docker compose up -d
mvn spring-boot:run
```

### Test

```bash
# Simple paging
curl 'http://localhost:8080/api/products?page=0&size=3&sort=name,asc'

# RSQL filter
curl 'http://localhost:8080/api/products?filter=price>100&sort=price,desc'

# Filter on relation
curl 'http://localhost:8080/api/products?filter=category.name==Electronics&size=5'

# AND combination
curl 'http://localhost:8080/api/products?filter=category.name==Electronics;price>500'
```

### Load tests (k6)

```bash
k6 run k6/paging-test.js
```

## API

### `RsqlPagingExecutor`

```java
// Simple hydration (findAllById)
executor.findPage(repository, Product.class, filter, sort, page, size);

// Custom hydration (recommended — fetch joins, entity graphs...)
executor.findPage(
    productRepository::findAllWithCategoryByIdIn,
    Product.class,
    filter,
    sort,
    page,
    size
);
```

### `RsqlPageResult<T>`

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

### `RsqlFilterBuilder`

Fluent builder that converts legacy query parameters to an RSQL filter string. Useful for migrating existing APIs that use individual query params (`?status=PAID&minPrice=100`) to RSQL without changing the client contract.

```java
var filter = RsqlFilterBuilder.from(params)
        .eq("status")                        // ?status=PAID       → status==PAID
        .eq("customer", "customer.name")     // ?customer=Dupont   → customer.name==Dupont
        .gte("minPrice", "price")            // ?minPrice=100      → price>=100
        .lte("maxPrice", "price")            // ?maxPrice=500      → price<=500
        .in("types", "type")                 // ?types=A,B,C       → type=in=(A,B,C)
        .like("search", "name")              // ?search=lap        → name==*lap*
        .build();                            // → joined with ";"
```

**Operators:**

| Method | RSQL output | Description |
|--------|-------------|-------------|
| `eq(param)` / `eq(param, field)` | `field==value` | Equals |
| `neq(param)` / `neq(param, field)` | `field!=value` | Not equals |
| `gt(param)` / `gt(param, field)` | `field>value` | Greater than |
| `gte(param)` / `gte(param, field)` | `field>=value` | Greater than or equal |
| `lt(param)` / `lt(param, field)` | `field<value` | Less than |
| `lte(param)` / `lte(param, field)` | `field<=value` | Less than or equal |
| `in(param)` / `in(param, field)` | `field=in=(a,b,c)` | In list |
| `out(param)` / `out(param, field)` | `field=out=(a,b,c)` | Not in list |
| `like(param)` / `like(param, field)` | `field==*value*` | Wildcard search |
| `raw(rsql)` | (as-is) | Append raw RSQL clause |

**Combining with an existing RSQL filter:**

```java
var filter = RsqlFilterBuilder.from(params)
        .eq("status")
        .buildAndCombine(params.get("filter"));  // merges both
```

Missing or blank params are silently skipped. See the [migration guide](docs/legacy-migration-guide.md) for detailed examples.

### RSQL syntax

| Operator | Meaning |
|----------|---------|
| `==` | Equals |
| `!=` | Not equals |
| `>`, `>=`, `<`, `<=` | Comparison |
| `=in=(a,b,c)` | In list |
| `=out=(a,b,c)` | Not in list |
| `;` | AND |
| `,` | OR |
| `field.subfield` | Relation traversal |

### Error handling

Errors return an [RFC 7807 ProblemDetail](https://www.rfc-editor.org/rfc/rfc7807):

| Error | HTTP Status |
|-------|-------------|
| Invalid RSQL filter | 400 Bad Request |
| Non-existent sort property | 400 Bad Request |
| Negative page or size <= 0 | 400 Bad Request |
| Result exceeds `max-id-count` | 413 Payload Too Large |

### Configuration

```yaml
rsql:
  paging:
    max-id-count: 1000000   # Max IDs loaded into memory (default: 1M)
```

## Integration

- [Integration guide](docs/integration-guide.md) — copy the lib into an existing project
- [Legacy migration guide](docs/legacy-migration-guide.md) — migrate query-param APIs to RSQL with `RsqlFilterBuilder`
