/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rsql.paging")
public record RsqlPagingProperties(int maxIdCount) {
    static final int DEFAULT_MAX_ID_COUNT = 1_000_000;

    public RsqlPagingProperties {
        if (maxIdCount <= 0) {
            throw new IllegalArgumentException("rsql.paging.max-id-count must be > 0, got: " + maxIdCount);
        }
    }

    public RsqlPagingProperties() {
        this(DEFAULT_MAX_ID_COUNT);
    }
}
