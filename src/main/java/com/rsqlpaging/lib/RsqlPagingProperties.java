package com.rsqlpaging.lib;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rsql.paging")
public record RsqlPagingProperties(int maxIdCount) {
    private static final int DEFAULT_MAX_ID_COUNT = 1_000_000;

    public RsqlPagingProperties {
        if (maxIdCount <= 0) {
            maxIdCount = DEFAULT_MAX_ID_COUNT;
        }
    }

    public RsqlPagingProperties() {
        this(DEFAULT_MAX_ID_COUNT);
    }
}
