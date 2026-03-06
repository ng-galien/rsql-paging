package com.rsqlpaging.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fluent builder that converts legacy query parameters to an RSQL filter string.
 *
 * <p>Example usage:
 * <pre>{@code
 * var filter = RsqlFilterBuilder.from(params)
 *         .eq("status")
 *         .eq("customer", "customer.name")
 *         .gte("minPrice", "price")
 *         .lte("maxPrice", "price")
 *         .in("types", "type")
 *         .like("search", "name")
 *         .build();
 * }</pre>
 */
public final class RsqlFilterBuilder {

    private final Map<String, String> params;
    private final List<String> clauses = new ArrayList<>();

    private RsqlFilterBuilder(Map<String, String> params) {
        this.params = Map.copyOf(params);
    }

    public static RsqlFilterBuilder from(Map<String, String> params) {
        return new RsqlFilterBuilder(params);
    }

    /** Maps param to same-named RSQL field with == operator. */
    public RsqlFilterBuilder eq(String param) {
        return eq(param, param);
    }

    /** Maps param to a different RSQL field with == operator. */
    public RsqlFilterBuilder eq(String param, String field) {
        return op(param, field, "==");
    }

    /** Maps param to same-named RSQL field with != operator. */
    public RsqlFilterBuilder neq(String param) {
        return neq(param, param);
    }

    /** Maps param to a different RSQL field with != operator. */
    public RsqlFilterBuilder neq(String param, String field) {
        return op(param, field, "!=");
    }

    /** Maps param to same-named RSQL field with > operator. */
    public RsqlFilterBuilder gt(String param) {
        return gt(param, param);
    }

    /** Maps param to a different RSQL field with > operator. */
    public RsqlFilterBuilder gt(String param, String field) {
        return op(param, field, ">");
    }

    /** Maps param to same-named RSQL field with >= operator. */
    public RsqlFilterBuilder gte(String param) {
        return gte(param, param);
    }

    /** Maps param to a different RSQL field with >= operator. */
    public RsqlFilterBuilder gte(String param, String field) {
        return op(param, field, ">=");
    }

    /** Maps param to same-named RSQL field with &lt; operator. */
    public RsqlFilterBuilder lt(String param) {
        return lt(param, param);
    }

    /** Maps param to a different RSQL field with &lt; operator. */
    public RsqlFilterBuilder lt(String param, String field) {
        return op(param, field, "<");
    }

    /** Maps param to same-named RSQL field with &lt;= operator. */
    public RsqlFilterBuilder lte(String param) {
        return lte(param, param);
    }

    /** Maps param to a different RSQL field with &lt;= operator. */
    public RsqlFilterBuilder lte(String param, String field) {
        return op(param, field, "<=");
    }

    /** Maps a comma-separated param to =in=(...) on same-named field. */
    public RsqlFilterBuilder in(String param) {
        return in(param, param);
    }

    /** Maps a comma-separated param to =in=(...) on a different field. */
    public RsqlFilterBuilder in(String param, String field) {
        var value = params.get(param);
        if (value != null && !value.isBlank()) {
            clauses.add("%s=in=(%s)".formatted(field, value));
        }
        return this;
    }

    /** Maps a comma-separated param to =out=(...) on same-named field. */
    public RsqlFilterBuilder out(String param) {
        return out(param, param);
    }

    /** Maps a comma-separated param to =out=(...) on a different field. */
    public RsqlFilterBuilder out(String param, String field) {
        var value = params.get(param);
        if (value != null && !value.isBlank()) {
            clauses.add("%s=out=(%s)".formatted(field, value));
        }
        return this;
    }

    /** Maps param to a wildcard search ==*value* on same-named field. */
    public RsqlFilterBuilder like(String param) {
        return like(param, param);
    }

    /** Maps param to a wildcard search ==*value* on a different field. */
    public RsqlFilterBuilder like(String param, String field) {
        var value = params.get(param);
        if (value != null && !value.isBlank()) {
            clauses.add("%s==*%s*".formatted(field, value));
        }
        return this;
    }

    /** Appends a raw RSQL clause (e.g. from an existing filter param). */
    public RsqlFilterBuilder raw(String rsql) {
        if (rsql != null && !rsql.isBlank()) {
            clauses.add(rsql);
        }
        return this;
    }

    private RsqlFilterBuilder op(String param, String field, String operator) {
        var value = params.get(param);
        if (value != null && !value.isBlank()) {
            clauses.add("%s%s%s".formatted(field, operator, value));
        }
        return this;
    }

    /** Builds the RSQL filter string by joining all clauses with AND (;). */
    public String build() {
        return String.join(";", clauses);
    }

    /**
     * Combines this builder's output with an existing RSQL filter string.
     * Useful for merging legacy params with a raw "filter" query param.
     */
    public String buildAndCombine(String existingFilter) {
        return Stream.of(build(), existingFilter == null ? "" : existingFilter.trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(";"));
    }
}
