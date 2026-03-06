/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * Fluent builder that converts legacy query parameters to a Spring {@link Sort}.
 *
 * <p>Example usage:
 * <pre>{@code
 * var sort = RsqlSortBuilder.from(params)
 *         .asc("sortByName", "name")
 *         .desc("sortByPrice", "price")
 *         .mapping("orderBy", "orderDir")
 *         .defaultSort("id", Sort.Direction.ASC)
 *         .build();
 * }</pre>
 */
public final class RsqlSortBuilder {

    private final Map<String, String> params;
    private final List<Sort.Order> orders = new ArrayList<>();
    private Sort.Order defaultOrder;

    private RsqlSortBuilder(Map<String, String> params) {
        this.params = Map.copyOf(params);
    }

    public static RsqlSortBuilder from(Map<String, String> params) {
        return new RsqlSortBuilder(params);
    }

    /**
     * If param is present and non-blank, adds an ascending sort on the given field.
     * The param value is ignored — only its presence matters.
     */
    public RsqlSortBuilder asc(String param, String field) {
        var value = params.get(param);
        if (value != null && !value.isBlank()) {
            orders.add(Sort.Order.asc(field));
        }
        return this;
    }

    /** Shorthand: param name equals field name. */
    public RsqlSortBuilder asc(String param) {
        return asc(param, param);
    }

    /**
     * If param is present and non-blank, adds a descending sort on the given field.
     * The param value is ignored — only its presence matters.
     */
    public RsqlSortBuilder desc(String param, String field) {
        var value = params.get(param);
        if (value != null && !value.isBlank()) {
            orders.add(Sort.Order.desc(field));
        }
        return this;
    }

    /** Shorthand: param name equals field name. */
    public RsqlSortBuilder desc(String param) {
        return desc(param, param);
    }

    /**
     * Maps a param whose <em>value</em> is the field to sort on, with direction from a separate param.
     *
     * <p>Example: {@code ?sortBy=price&sortDir=desc} →
     * {@code mapping("sortBy", "sortDir")} → {@code Sort.by(DESC, "price")}
     *
     * <p>If directionParam is absent or unrecognized, defaults to ASC.
     */
    public RsqlSortBuilder mapping(String fieldParam, String directionParam) {
        var field = params.get(fieldParam);
        if (field != null && !field.isBlank()) {
            var dir = parseDirection(params.get(directionParam));
            orders.add(new Sort.Order(dir, field));
        }
        return this;
    }

    /**
     * Maps a single param whose value contains both field and direction separated by a comma.
     *
     * <p>Example: {@code ?sort=price,desc} →
     * {@code sort("sort")} → {@code Sort.by(DESC, "price")}
     *
     * <p>If no comma, the whole value is the field with ASC direction.
     */
    public RsqlSortBuilder sort(String param) {
        var value = params.get(param);
        if (value != null && !value.isBlank()) {
            var parts = value.split(",", 2);
            var field = parts[0].trim();
            if (!field.isEmpty()) {
                var dir = parts.length > 1 ? parseDirection(parts[1].trim()) : Sort.Direction.ASC;
                orders.add(new Sort.Order(dir, field));
            }
        }
        return this;
    }

    /** Sets a default sort used when no orders have been added. */
    public RsqlSortBuilder defaultSort(String field, Sort.Direction direction) {
        this.defaultOrder = new Sort.Order(direction, field);
        return this;
    }

    /** Builds the {@link Sort}. Returns unsorted if no orders and no default. */
    public Sort build() {
        if (orders.isEmpty() && defaultOrder != null) {
            return Sort.by(defaultOrder);
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }

    private static Sort.Direction parseDirection(String value) {
        if (value == null) {
            return Sort.Direction.ASC;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "desc", "descending" -> Sort.Direction.DESC;
            default -> Sort.Direction.ASC;
        };
    }
}
