# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Requires Java 21 and Maven (no wrapper)
mvn compile                        # Compile
mvn spring-boot:run                # Run (needs PostgreSQL on port 5433)
docker compose up -d               # Start PostgreSQL 17
docker compose down                # Stop PostgreSQL
mvn dependency:tree                # Show dependency graph
```

Database: PostgreSQL on `localhost:5433`, credentials `rsqlpaging/rsqlpaging`. Schema is auto-created (`ddl-auto: create-drop`), data loaded from `data.sql`.

## Architecture

Two packages with distinct roles:

### `com.rsqlpaging.lib` — Reusable library

The core of the project. Designed to be imported as a dependency in other Spring Boot 3 projects via auto-configuration (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).

- **`RsqlPagingExecutor`** — Implements the 3-step ID-first paging strategy:
  1. **ID query**: CriteriaQuery selecting only IDs, filtered by RSQL (`RSQLJPASupport.toSpecification`), sorted, deduplicated in Java via `LinkedHashSet`
  2. **In-memory slicing**: `subList(page * size, ...)`, count = `ids.size()` — no SQL COUNT, no OFFSET
  3. **Hydration**: caller-provided `Function<List<ID>, List<T>>` loads full entities (fetch joins, entity graphs, etc.), then results are reordered to match the sort via `PersistenceUnitUtil.getIdentifier`

  Two overloads: one accepting `JpaRepository` (defaults to `findAllById`), one accepting a custom hydrator function. The custom hydrator is the recommended path to avoid N+1/LazyInitializationException with `open-in-view: false`.

- **`RsqlPageResult`** — Immutable record with compact constructor validation. Created only via `of()` / `empty()` factory methods.
- **`RsqlPagingAutoConfiguration`** — `@AutoConfiguration` registering the executor bean.
- **`RsqlPagingExceptionHandler`** — `@RestControllerAdvice` mapping `RSQLParserException`, `IllegalArgumentException`, `UnsupportedOperationException` to RFC 7807 `ProblemDetail` (400).

### `com.rsqlpaging.demo` — Demo application

Example Spring Boot app showing library usage with `Product`/`Category` entities. Key pattern: the controller passes `productRepository::findAllWithCategoryByIdIn` as hydrator, which uses `LEFT JOIN FETCH` to load the lazy `@ManyToOne` relationship in a single query.

## Key Design Decisions

- **All IDs in memory**: intentional trade-off. Works well for typical volumes; not suited for millions of rows per query.
- **No DISTINCT in SQL**: `SELECT DISTINCT id ORDER BY non_id_column` fails on some databases. Deduplication happens in Java with `LinkedHashSet` (preserves insertion order).
- **Composite keys not supported**: `resolveIdFieldName` requires `hasSingleIdAttribute()`. Throws `UnsupportedOperationException` for `@IdClass`/`@EmbeddedId`.
- **Deterministic fallback sort**: when no sort is provided, automatically sorts by the entity's `@Id` field to guarantee stable pagination.
- **Sort property validation**: properties are checked against the JPA metamodel before query execution. Dot-notation supported for relationships (e.g., `category.name`).

## RSQL Filter Syntax

Uses [rsql-jpa-specification](https://github.com/perplexhub/rsql-jpa-specification) v6.0.23. Example filters:
- `name==Laptop` — equality
- `price>100` — comparison
- `name==Laptop;price>100` — AND
- `name==Laptop,name==Tablet` — OR
- `category.name==Electronics` — relationship traversal
