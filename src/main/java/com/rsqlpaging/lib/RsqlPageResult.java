package com.rsqlpaging.lib;

import java.util.List;

public record RsqlPageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> RsqlPageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        boolean first = page == 0;
        boolean last = size <= 0 || (long) (page + 1) * size >= totalElements;
        return new RsqlPageResult<>(content, page, size, totalElements, totalPages, first, last);
    }

    public static <T> RsqlPageResult<T> empty(int page, int size, long totalElements) {
        return of(List.of(), page, size, totalElements);
    }
}
