# Integration guide

Three ways to integrate rsql-paging into an existing Spring Boot 3 project.

## Option A: Maven dependency (JitPack) — recommended

No authentication required. JitPack builds directly from GitHub tags.

### 1. Add the repository and dependency

In your project's `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.ng-galien</groupId>
        <artifactId>rsql-paging</artifactId>
        <version>v0.4.0</version>
    </dependency>
</dependencies>
```

The library pulls in `rsql-jpa-spring-boot-starter` transitively. Your project must already have `spring-boot-starter-data-jpa` and `spring-boot-starter-web`.

Auto-configuration is automatic — `RsqlPagingExecutor` is registered as a bean via Spring Boot's `AutoConfiguration.imports`.

Skip to [step 3: Create the hydration method](#3-create-the-hydration-method-in-the-repository).

## Option B: Maven dependency (GitHub Packages)

> **Note**: GitHub Packages requires authentication even for public repositories.

### 1. Authentication

Create a [personal access token](https://github.com/settings/tokens) with `read:packages` scope, then add it to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

### 2. Add the repository and dependency

In your project's `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/ng-galien/rsql-paging</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.nggalien</groupId>
        <artifactId>rsql-paging</artifactId>
        <version>0.4.0</version>
    </dependency>
</dependencies>
```

The library pulls in `rsql-jpa-spring-boot-starter` transitively. Your project must already have `spring-boot-starter-data-jpa` and `spring-boot-starter-web`.

Auto-configuration is automatic — `RsqlPagingExecutor` is registered as a bean via Spring Boot's `AutoConfiguration.imports`.

Skip to [step 3: Create the hydration method](#3-create-the-hydration-method-in-the-repository).

## Option C: Copy-based integration

### 1. Maven dependency

Add the rsql-jpa-specification dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.perplexhub</groupId>
    <artifactId>rsql-jpa-spring-boot-starter</artifactId>
    <version>6.0.34</version>
</dependency>
```

Your project must already have `spring-boot-starter-data-jpa` and `spring-boot-starter-web`.

### 2. Copy the library files

Copy the 7 files from the `io.github.nggalien.rsqlpaging` package into your project, adapting the package name:

```
src/main/java/io/github/nggalien/rsqlpaging/
├── RsqlPagingExecutor.java          # Core — 3-step pagination
├── RsqlPageResult.java              # Result record
├── RsqlPagingProperties.java        # Configuration (max-id-count)
├── RsqlPagingAutoConfiguration.java # Spring Boot auto-configuration
├── RsqlPagingExceptionHandler.java  # Error handling → ProblemDetail
├── RsqlFilterBuilder.java           # Legacy query params → RSQL filter
└── RsqlSortBuilder.java             # Legacy query params → Spring Sort
```

#### Adapt the package

Rename the package in all files. For example, for a project `com.myproject`:

```
io.github.nggalien.rsqlpaging → com.myproject.rsqlpaging
```

#### Register auto-configuration

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

- [ ] Library added (Maven dependency or 7 files copied with correct package)
- [ ] Auto-configuration registered (automatic with Maven, or via imports/`@Configuration` for copy)
- [ ] `rsql-jpa-spring-boot-starter` available (transitive with Maven, or explicit dependency for copy)
- [ ] Hydration method with `LEFT JOIN FETCH` in the repository
- [ ] Controller using `rsqlPagingExecutor.findPage(...)`
- [ ] `open-in-view: false` in application.yml
