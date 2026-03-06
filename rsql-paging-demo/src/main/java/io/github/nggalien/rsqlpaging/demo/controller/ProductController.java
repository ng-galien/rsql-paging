/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging.demo.controller;

import io.github.nggalien.rsqlpaging.RsqlPageResult;
import io.github.nggalien.rsqlpaging.RsqlPagingExecutor;
import io.github.nggalien.rsqlpaging.demo.entity.Product;
import io.github.nggalien.rsqlpaging.demo.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final RsqlPagingExecutor rsqlPagingExecutor;

    public ProductController(ProductRepository productRepository, RsqlPagingExecutor rsqlPagingExecutor) {
        this.productRepository = productRepository;
        this.rsqlPagingExecutor = rsqlPagingExecutor;
    }

    @GetMapping
    public RsqlPageResult<Product> findProducts(
            @RequestParam(defaultValue = "") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Sort sort) {

        return rsqlPagingExecutor.findPage(
                productRepository::findAllWithCategoryByIdIn, Product.class, filter, sort, page, size);
    }
}
