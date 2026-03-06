# Integration guide (copy-based)

This guide explains how to integrate the rsql-paging library into an existing Spring Boot 3 project by copying the source files directly.

## 1. Maven dependency

Add the rsql-jpa-specification dependency to the target project's `pom.xml`:

```xml
<dependency>
    <groupId>io.github.perplexhub</groupId>
    <artifactId>rsql-jpa-spring-boot-starter</artifactId>
    <version>6.0.23</version>
</dependency>
```

The project must already have `spring-boot-starter-data-jpa` and `spring-boot-starter-web`.

## 2. Copy the library files

Copy the 5 files from the `com.rsqlpaging.lib` package into the target project, adapting the package name:

```
src/main/java/com/rsqlpaging/lib/
├── RsqlPagingExecutor.java          # Core — 3-step pagination
├── RsqlPageResult.java              # Result record
├── RsqlPagingProperties.java        # Configuration (max-id-count)
├── RsqlPagingAutoConfiguration.java # Spring Boot auto-configuration
└── RsqlPagingExceptionHandler.java  # Error handling → ProblemDetail
```

### Adapt the package

Rename the package in all files. For example, for a project `com.myproject`:

```
com.rsqlpaging.lib → com.myproject.rsqlpaging
```

### Register auto-configuration

Create (or update) the file:

```
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

With:

```
com.myproject.rsqlpaging.RsqlPagingAutoConfiguration
```

> **Alternative**: if the package is already scanned by `@SpringBootApplication`, you can simply add `@Configuration` to `RsqlPagingAutoConfiguration` and remove the `AutoConfiguration.imports` file.

## 3. Create the hydration method in the repository

For each paginated entity, add a method with `LEFT JOIN FETCH` on lazy associations:

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.customer " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.id IN :ids")
    List<Order> findAllWithAssociationsByIdIn(@Param("ids") List<Long> ids);
}
```

**Important notes:**
- Always use `LEFT JOIN FETCH` (not `JOIN FETCH`) to avoid excluding entities with null relationships
- Fetch all associations that will be serialized to JSON
- If `open-in-view: false` (recommended), any unfetched lazy association will cause a `LazyInitializationException`

## 4. Use in a controller

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final RsqlPagingExecutor rsqlPagingExecutor;

    public OrderController(OrderRepository orderRepository, RsqlPagingExecutor rsqlPagingExecutor) {
        this.orderRepository = orderRepository;
        this.rsqlPagingExecutor = rsqlPagingExecutor;
    }

    @GetMapping
    public RsqlPageResult<Order> findOrders(
            @RequestParam(defaultValue = "") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Sort sort) {

        return rsqlPagingExecutor.findPage(
                orderRepository::findAllWithAssociationsByIdIn,
                Order.class,
                filter,
                sort,
                page,
                size
        );
    }
}
```

### Example request

```
GET /api/orders?filter=customer.name==Dupont;status==PAID&sort=createdAt,desc&page=0&size=10
```

## 5. Automatic behaviors

| Behavior | Detail |
|---|---|
| **Default sort** | If no `sort` is provided, automatically sorts by the entity's `@Id` field |
| **Sort validation** | Sort properties are validated against the JPA metamodel. Unknown property → 400 |
| **Page/size validation** | `page < 0` or `size < 1` → 400 |
| **Invalid RSQL** | Malformed filter → 400 with ProblemDetail message |
| **Deduplication** | Duplicate IDs (from joins in the RSQL filter) are deduplicated in Java |
| **Reordering** | Hydrated entities are reordered to match the requested sort |
| **Hard limit** | Default 1M IDs max. Beyond that → 413 Payload Too Large. Configurable via `rsql.paging.max-id-count` |

## 6. Configuration

```yaml
spring:
  jpa:
    open-in-view: false    # Required to avoid session leaks

rsql:
  paging:
    max-id-count: 1000000  # Max IDs loaded into memory (default: 1M)
```

The ID query uses `setMaxResults(maxIdCount + 1)` on the SQL side to never load more than necessary from the database. If the filter returns more IDs than the limit, the query fails immediately with a **413 Payload Too Large** and the message:

> Query returned more than 1000000 IDs. Narrow your filter or increase rsql.paging.max-id-count.

## 7. Limitations

- **Composite keys**: `@IdClass` and `@EmbeddedId` are not supported. The entity must have a single `@Id` field.
- **Volume**: all IDs matching the filter are loaded into memory. Suitable for tables up to a few hundred thousand rows. Beyond that, consider keyset pagination.
- **Fetched collections**: if the hydration uses `JOIN FETCH` on a `@OneToMany`, Hibernate may return duplicates. Use `DISTINCT` in the JPQL query or `@EntityGraph` instead.

## Integration checklist

- [ ] `rsql-jpa-spring-boot-starter` dependency added to pom.xml
- [ ] 5 library files copied with the correct package
- [ ] Auto-configuration registered (imports or @Configuration)
- [ ] Hydration method with `LEFT JOIN FETCH` in the repository
- [ ] Controller using `rsqlPagingExecutor.findPage(...)`
- [ ] `open-in-view: false` in application.yml
