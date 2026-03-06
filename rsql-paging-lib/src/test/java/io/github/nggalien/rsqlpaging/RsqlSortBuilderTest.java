/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class RsqlSortBuilderTest {

    // --- asc ---

    @Test
    void asc_presentParam_shouldAddAscendingOrder() {
        var sort = RsqlSortBuilder.from(Map.of("sortByName", "true"))
                .asc("sortByName", "name")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void asc_sameField_shouldUseParamAsField() {
        var sort = RsqlSortBuilder.from(Map.of("name", "true")).asc("name").build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void asc_missingParam_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of()).asc("sortByName", "name").build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    @Test
    void asc_blankParam_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of("sortByName", "  "))
                .asc("sortByName", "name")
                .build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    // --- desc ---

    @Test
    void desc_presentParam_shouldAddDescendingOrder() {
        var sort = RsqlSortBuilder.from(Map.of("sortByPrice", "true"))
                .desc("sortByPrice", "price")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void desc_sameField_shouldUseParamAsField() {
        var sort = RsqlSortBuilder.from(Map.of("price", "true")).desc("price").build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void desc_missingParam_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of()).desc("sortByPrice", "price").build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    @Test
    void desc_blankParam_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of("sortByPrice", " "))
                .desc("sortByPrice", "price")
                .build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    // --- mapping ---

    @Test
    void mapping_shouldUsFieldValueAndDirection() {
        var sort = RsqlSortBuilder.from(Map.of("sortBy", "price", "sortDir", "desc"))
                .mapping("sortBy", "sortDir")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void mapping_ascDirection_shouldDefaultToAsc() {
        var sort = RsqlSortBuilder.from(Map.of("sortBy", "name", "sortDir", "asc"))
                .mapping("sortBy", "sortDir")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void mapping_missingDirection_shouldDefaultToAsc() {
        var sort = RsqlSortBuilder.from(Map.of("sortBy", "name"))
                .mapping("sortBy", "sortDir")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void mapping_descendingFullWord_shouldParseDesc() {
        var sort = RsqlSortBuilder.from(Map.of("orderBy", "price", "orderDir", "descending"))
                .mapping("orderBy", "orderDir")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void mapping_missingField_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of("sortDir", "desc"))
                .mapping("sortBy", "sortDir")
                .build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    @Test
    void mapping_blankField_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of("sortBy", "  ", "sortDir", "desc"))
                .mapping("sortBy", "sortDir")
                .build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    // --- sort (single param with comma) ---

    @Test
    void sort_fieldAndDesc_shouldParse() {
        var sort =
                RsqlSortBuilder.from(Map.of("sort", "price,desc")).sort("sort").build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void sort_fieldAndAsc_shouldParse() {
        var sort = RsqlSortBuilder.from(Map.of("sort", "name,asc")).sort("sort").build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void sort_fieldOnly_shouldDefaultToAsc() {
        var sort = RsqlSortBuilder.from(Map.of("sort", "name")).sort("sort").build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void sort_missingParam_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of()).sort("sort").build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    @Test
    void sort_blankParam_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of("sort", " ")).sort("sort").build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    @Test
    void sort_blankFieldBeforeComma_shouldBeSkipped() {
        var sort = RsqlSortBuilder.from(Map.of("sort", " ,desc")).sort("sort").build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    // --- defaultSort ---

    @Test
    void defaultSort_noOrders_shouldUseDefault() {
        var sort = RsqlSortBuilder.from(Map.of())
                .defaultSort("id", Sort.Direction.ASC)
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("id")));
    }

    @Test
    void defaultSort_withOrders_shouldIgnoreDefault() {
        var sort = RsqlSortBuilder.from(Map.of("sortByName", "true"))
                .asc("sortByName", "name")
                .defaultSort("id", Sort.Direction.ASC)
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void defaultSort_desc_shouldWork() {
        var sort = RsqlSortBuilder.from(Map.of())
                .defaultSort("createdAt", Sort.Direction.DESC)
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("createdAt")));
    }

    // --- build: no orders, no default ---

    @Test
    void build_noOrdersNoDefault_shouldReturnUnsorted() {
        var sort = RsqlSortBuilder.from(Map.of()).build();

        assertThat(sort).isEqualTo(Sort.unsorted());
    }

    // --- multiple orders ---

    @Test
    void multipleOrders_shouldPreserveInsertionOrder() {
        var sort = RsqlSortBuilder.from(Map.of("sortByName", "true", "sortByPrice", "true"))
                .asc("sortByName", "name")
                .desc("sortByPrice", "price")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name"), Sort.Order.desc("price")));
    }

    @Test
    void multipleOrders_mixedPresentAndMissing_shouldOnlyIncludePresent() {
        var sort = RsqlSortBuilder.from(Map.of("sortByName", "true"))
                .asc("sortByName", "name")
                .desc("sortByPrice", "price")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    // --- Immutability ---

    @Test
    void from_shouldCopyParams() {
        var params = new java.util.HashMap<String, String>();
        params.put("sortBy", "price");
        params.put("sortDir", "desc");
        var builder = RsqlSortBuilder.from(params);
        params.put("sortBy", "name");

        var sort = builder.mapping("sortBy", "sortDir").build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    // --- Full realistic scenario ---

    @Test
    void fullScenario_legacySearchWithSortMapping() {
        var params = Map.of("orderBy", "price", "orderDir", "desc");

        var sort = RsqlSortBuilder.from(params)
                .mapping("orderBy", "orderDir")
                .defaultSort("id", Sort.Direction.ASC)
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void fullScenario_legacySearchFallbackToDefault() {
        var sort = RsqlSortBuilder.from(Map.of())
                .mapping("orderBy", "orderDir")
                .defaultSort("id", Sort.Direction.ASC)
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("id")));
    }

    // --- direction parsing edge cases ---

    @Test
    void mapping_unknownDirection_shouldDefaultToAsc() {
        var sort = RsqlSortBuilder.from(Map.of("sortBy", "name", "sortDir", "invalid"))
                .mapping("sortBy", "sortDir")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void mapping_directionWithWhitespace_shouldBeTrimmed() {
        var sort = RsqlSortBuilder.from(Map.of("sortBy", "name", "sortDir", "  DESC  "))
                .mapping("sortBy", "sortDir")
                .build();

        assertThat(sort).isEqualTo(Sort.by(Sort.Order.desc("name")));
    }
}
