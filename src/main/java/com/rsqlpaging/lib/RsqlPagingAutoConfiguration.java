package com.rsqlpaging.lib;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class RsqlPagingAutoConfiguration {

    @Bean
    public RsqlPagingExecutor rsqlPagingExecutor(EntityManager entityManager) {
        return new RsqlPagingExecutor(entityManager);
    }
}
