/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(RsqlPagingProperties.class)
public class RsqlPagingAutoConfiguration {

    @Bean
    public RsqlPagingExecutor rsqlPagingExecutor(EntityManager entityManager, RsqlPagingProperties properties) {
        return new RsqlPagingExecutor(entityManager, properties.maxIdCount());
    }
}
