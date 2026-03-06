# Legacy migration guide

This guide shows how to migrate a traditional query-parameter-based API to RSQL filtering using `RsqlFilterBuilder`.

## The problem

Many REST APIs accept individual query parameters for filtering:

```
GET /api/products?status=ACTIVE&minPrice=100&maxPrice=500&category=Electronics
```

Each parameter requires manual handling in the controller and repository — custom `Specification` builders, conditional `WHERE` clauses, or growing `if` chains. Adding a new filter means touching multiple layers.

## The solution

`RsqlFilterBuilder` converts legacy query parameters into a single RSQL filter string, which is then processed by the existing `RsqlPagingExecutor`. You keep your existing API contract while eliminating boilerplate.

## Step-by-step migration

### Before: manual filtering

```java
@GetMapping
public Page<Product> findProducts(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String search,
        Pageable pageable) {

    Specification<Product> spec = Specification.where(null);
    if (status != null) {
        spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (minPrice != null) {
        spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
    }
    if (maxPrice != null) {
        spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
    }
    if (category != null) {
        spec = spec.and((root, query, cb) -> cb.equal(root.join("category").get("name"), category));
    }
    if (search != null) {
        spec = spec.and((root, query, cb) -> cb.like(root.get("name"), "%" + search + "%"));
    }
    return productRepository.findAll(spec, pageable);
}
```

### After: RsqlFilterBuilder

```java
@GetMapping
public RsqlPageResult<Product> findProducts(
        @RequestParam Map<String, String> params,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Sort sort) {

    var filter = RsqlFilterBuilder.from(params)
            .eq("status")
            .gte("minPrice", "price")
            .lte("maxPrice", "price")
            .eq("category", "category.name")
            .like("search", "name")
            .build();

    return rsqlPagingExecutor.findPage(
            productRepository::findAllWithCategoryByIdIn,
            Product.class,
            filter,
            sort,
            page,
            size
    );
}
```

The API contract stays the same — callers still use `?status=ACTIVE&minPrice=100`. The implementation is now declarative and requires no `Specification` boilerplate.

## Operator reference

| Method | RSQL output | Use case |
|--------|-------------|----------|
| `eq("status")` | `status==ACTIVE` | Exact match, same field name |
| `eq("category", "category.name")` | `category.name==Electronics` | Exact match, different field |
| `neq("excluded", "status")` | `status!=CANCELLED` | Not equals |
| `gt("minRating", "rating")` | `rating>4` | Greater than |
| `gte("minPrice", "price")` | `price>=100` | Greater than or equal |
| `lt("maxAge", "age")` | `age<30` | Less than |
| `lte("maxPrice", "price")` | `price<=500` | Less than or equal |
| `in("types", "type")` | `type=in=(A,B,C)` | In list (comma-separated param) |
| `out("excluded", "status")` | `status=out=(X,Y)` | Not in list |
| `like("search", "name")` | `name==*laptop*` | Wildcard search |
| `raw(rsql)` | (as-is) | Append a raw RSQL clause |

Each method takes either one argument (param name = field name) or two arguments (param name, RSQL field name).

## Handling blank and missing parameters

Missing or blank parameters are silently skipped. No null checks needed:

```java
// params = { "status": "ACTIVE" }  (no minPrice, no maxPrice)
var filter = RsqlFilterBuilder.from(params)
        .eq("status")              // → status==ACTIVE
        .gte("minPrice", "price")  // → skipped (not in params)
        .lte("maxPrice", "price")  // → skipped (not in params)
        .build();
// Result: "status==ACTIVE"
```

Blank values (`""` or whitespace-only) are also skipped:

```java
// params = { "status": "  ", "name": "" }
var filter = RsqlFilterBuilder.from(params)
        .eq("status")   // → skipped (blank)
        .eq("name")     // → skipped (empty)
        .build();
// Result: ""
```

## Combining with an existing RSQL filter parameter

If your API also accepts a raw `filter` parameter for advanced users, use `buildAndCombine()`:

```java
@GetMapping
public RsqlPageResult<Product> findProducts(
        @RequestParam Map<String, String> params,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Sort sort) {

    var filter = RsqlFilterBuilder.from(params)
            .eq("status")
            .gte("minPrice", "price")
            .lte("maxPrice", "price")
            .buildAndCombine(params.get("filter"));

    return rsqlPagingExecutor.findPage(
            productRepository::findAllWithCategoryByIdIn,
            Product.class,
            filter,
            sort,
            page,
            size
    );
}
```

This lets callers use either style:

```
GET /api/products?status=ACTIVE&minPrice=100
GET /api/products?filter=status==ACTIVE;price>=100
GET /api/products?status=ACTIVE&filter=price>=100    # both combined
```

## Gradual migration strategy

1. **Add the library** — follow the [integration guide](integration-guide.md)
2. **Keep existing endpoints** — don't break callers
3. **Add `RsqlFilterBuilder`** to each endpoint one at a time, replacing `Specification` logic
4. **Optionally expose `filter`** — add a `filter` query param for power users who want raw RSQL
5. **Remove old `Specification` code** once all endpoints are migrated

The key advantage: your API contract doesn't change. Callers keep using the same query parameters they always used.

## In-list parameters

For parameters that accept comma-separated values, use `in()` or `out()`:

```java
// GET /api/products?types=ELECTRONICS,CLOTHING,FOOD
var filter = RsqlFilterBuilder.from(params)
        .in("types", "type")
        .build();
// Result: "type=in=(ELECTRONICS,CLOTHING,FOOD)"
```

The comma-separated value is passed directly to RSQL's `=in=()` operator.

## Complex example

A real-world order search endpoint combining multiple filter types:

```java
@GetMapping
public RsqlPageResult<Order> searchOrders(
        @RequestParam Map<String, String> params,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        Sort sort) {

    var filter = RsqlFilterBuilder.from(params)
            .eq("status")
            .eq("customer", "customer.name")
            .gte("from", "createdAt")
            .lte("to", "createdAt")
            .gt("minTotal", "totalAmount")
            .in("priorities", "priority")
            .out("excludeStatuses", "status")
            .like("search", "reference")
            .buildAndCombine(params.get("filter"));

    return rsqlPagingExecutor.findPage(
            orderRepository::findAllWithAssociationsByIdIn,
            Order.class,
            filter,
            sort,
            page,
            size
    );
}
```

Request:

```
GET /api/orders?customer=Acme&from=2024-01-01&to=2024-12-31&status=PAID&priorities=HIGH,URGENT&search=ORD-2024&sort=createdAt,desc&page=0&size=50
```

Generated RSQL:

```
status==PAID;customer.name==Acme;createdAt>=2024-01-01;createdAt<=2024-12-31;priority=in=(HIGH,URGENT);reference==*ORD-2024*
```
