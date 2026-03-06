package com.rsqlpaging.demo.controller;

import com.rsqlpaging.demo.entity.Product;
import com.rsqlpaging.demo.repository.ProductRepository;
import com.rsqlpaging.lib.RsqlPageResult;
import com.rsqlpaging.lib.RsqlPagingExecutor;
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
