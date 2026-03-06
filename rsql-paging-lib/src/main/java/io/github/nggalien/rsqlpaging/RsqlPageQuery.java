/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import io.github.perplexhub.rsql.QuerySupport;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Fluent builder for constructing paginated RSQL queries.
 *
 * <pre>{@code
 * executor.query(Product.class)
 *     .filter("price>100")
 *     .sort(Sort.by("name"))
 *     .page(0, 20)
 *     .spec(s -> s.and(tenantSpec))
 *     .hydrator(productRepo::findAllWithCategoryByIdIn)
 *     .execute();
 * }</pre>
 */
public final class RsqlPageQuery<T, ID> {

    private final RsqlPagingExecutor executor;
    private final Class<T> entityClass;
    private String filter;
    private Sort sort;
    private int page;
    private int size = 20;
    private UnaryOperator<Specification<T>> specCustomizer = UnaryOperator.identity();
    private Consumer<QuerySupport.QuerySupportBuilder> rsqlCustomizer;
    private Function<List<ID>, List<T>> hydrator;

    RsqlPageQuery(RsqlPagingExecutor executor, Class<T> entityClass) {
        this.executor = executor;
        this.entityClass = entityClass;
    }

    /** Set the RSQL filter string (e.g. "name==Laptop;price>100"). */
    public RsqlPageQuery<T, ID> filter(String rsqlFilter) {
        this.filter = rsqlFilter;
        return this;
    }

    /** Set the sort order. */
    public RsqlPageQuery<T, ID> sort(Sort sortOrder) {
        this.sort = sortOrder;
        return this;
    }

    /** Set the page number and page size. */
    public RsqlPageQuery<T, ID> page(int pageNumber, int pageSize) {
        this.page = pageNumber;
        this.size = pageSize;
        return this;
    }

    /** Add extra where clauses via a specification customizer (e.g. tenant filter, security). */
    public RsqlPageQuery<T, ID> spec(UnaryOperator<Specification<T>> customizer) {
        this.specCustomizer = customizer;
        return this;
    }

    /** Customize the RSQL QuerySupport (whitelist, blacklist, property mapping, join hints...). */
    public RsqlPageQuery<T, ID> rsql(Consumer<QuerySupport.QuerySupportBuilder> customizer) {
        this.rsqlCustomizer = customizer;
        return this;
    }

    /** Set a custom hydrator function (fetch joins, entity graphs, projections...). */
    public RsqlPageQuery<T, ID> hydrator(Function<List<ID>, List<T>> hydratorFunction) {
        this.hydrator = hydratorFunction;
        return this;
    }

    /** Set hydration via a JpaRepository (uses findAllById). */
    public RsqlPageQuery<T, ID> repository(JpaRepository<T, ID> repository) {
        this.hydrator = repository::findAllById;
        return this;
    }

    /** Execute the query and return the paginated result. */
    public RsqlPageResult<T> execute() {
        if (hydrator == null) {
            throw new IllegalStateException("No hydrator set. Call hydrator() or repository() before execute().");
        }
        return executor.executePage(hydrator, entityClass, filter, sort, page, size, specCustomizer, rsqlCustomizer);
    }
}
