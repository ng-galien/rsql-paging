/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import java.util.List;
import java.util.Objects;

public record RsqlPageResult<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {

    public RsqlPageResult {
        Objects.requireNonNull(content, "content must not be null");
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        if (totalElements < 0) throw new IllegalArgumentException("totalElements must be >= 0");
        content = List.copyOf(content);
    }

    public static <T> RsqlPageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        boolean first = page == 0;
        boolean last = (long) (page + 1) * size >= totalElements;
        return new RsqlPageResult<>(content, page, size, totalElements, totalPages, first, last);
    }

    public static <T> RsqlPageResult<T> empty(int page, int size, long totalElements) {
        return of(List.of(), page, size, totalElements);
    }
}
