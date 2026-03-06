package com.rsqlpaging.lib;

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
