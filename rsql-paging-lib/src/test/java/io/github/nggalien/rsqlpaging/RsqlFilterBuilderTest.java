package io.github.nggalien.rsqlpaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RsqlFilterBuilderTest {

    // --- eq ---

    @Test
    void eq_sameField_shouldProduceEquals() {
        var filter =
                RsqlFilterBuilder.from(Map.of("status", "ACTIVE")).eq("status").build();

        assertThat(filter).isEqualTo("status==ACTIVE");
    }

    @Test
    void eq_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("customer", "Dupont"))
                .eq("customer", "customer.name")
                .build();

        assertThat(filter).isEqualTo("customer.name==Dupont");
    }

    @Test
    void eq_missingParam_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of()).eq("status").build();

        assertThat(filter).isEmpty();
    }

    @Test
    void eq_blankParam_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of("status", "  ")).eq("status").build();

        assertThat(filter).isEmpty();
    }

    // --- neq ---

    @Test
    void neq_sameField_shouldProduceNotEquals() {
        var filter = RsqlFilterBuilder.from(Map.of("status", "CANCELLED"))
                .neq("status")
                .build();

        assertThat(filter).isEqualTo("status!=CANCELLED");
    }

    @Test
    void neq_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("excludeStatus", "DRAFT"))
                .neq("excludeStatus", "status")
                .build();

        assertThat(filter).isEqualTo("status!=DRAFT");
    }

    // --- gt / gte / lt / lte ---

    @Test
    void gt_sameField_shouldProduceGreaterThan() {
        var filter = RsqlFilterBuilder.from(Map.of("price", "100")).gt("price").build();

        assertThat(filter).isEqualTo("price>100");
    }

    @Test
    void gt_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("minPrice", "100"))
                .gt("minPrice", "price")
                .build();

        assertThat(filter).isEqualTo("price>100");
    }

    @Test
    void gte_sameField_shouldProduceGreaterThanOrEqual() {
        var filter = RsqlFilterBuilder.from(Map.of("price", "100")).gte("price").build();

        assertThat(filter).isEqualTo("price>=100");
    }

    @Test
    void gte_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("minPrice", "100"))
                .gte("minPrice", "price")
                .build();

        assertThat(filter).isEqualTo("price>=100");
    }

    @Test
    void lt_sameField_shouldProduceLessThan() {
        var filter = RsqlFilterBuilder.from(Map.of("price", "500")).lt("price").build();

        assertThat(filter).isEqualTo("price<500");
    }

    @Test
    void lt_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("maxPrice", "500"))
                .lt("maxPrice", "price")
                .build();

        assertThat(filter).isEqualTo("price<500");
    }

    @Test
    void lte_sameField_shouldProduceLessThanOrEqual() {
        var filter = RsqlFilterBuilder.from(Map.of("price", "500")).lte("price").build();

        assertThat(filter).isEqualTo("price<=500");
    }

    @Test
    void lte_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("maxPrice", "500"))
                .lte("maxPrice", "price")
                .build();

        assertThat(filter).isEqualTo("price<=500");
    }

    // --- in / out ---

    @Test
    void in_sameField_shouldProduceInClause() {
        var filter = RsqlFilterBuilder.from(Map.of("type", "A,B,C")).in("type").build();

        assertThat(filter).isEqualTo("type=in=(A,B,C)");
    }

    @Test
    void in_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("types", "A,B,C"))
                .in("types", "type")
                .build();

        assertThat(filter).isEqualTo("type=in=(A,B,C)");
    }

    @Test
    void in_missingParam_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of()).in("types", "type").build();

        assertThat(filter).isEmpty();
    }

    @Test
    void in_blankParam_shouldBeSkipped() {
        var filter =
                RsqlFilterBuilder.from(Map.of("types", " ")).in("types", "type").build();

        assertThat(filter).isEmpty();
    }

    @Test
    void out_sameField_shouldProduceOutClause() {
        var filter = RsqlFilterBuilder.from(Map.of("status", "DRAFT,CANCELLED"))
                .out("status")
                .build();

        assertThat(filter).isEqualTo("status=out=(DRAFT,CANCELLED)");
    }

    @Test
    void out_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("excludeStatuses", "DRAFT"))
                .out("excludeStatuses", "status")
                .build();

        assertThat(filter).isEqualTo("status=out=(DRAFT)");
    }

    @Test
    void out_missingParam_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of()).out("status").build();

        assertThat(filter).isEmpty();
    }

    @Test
    void out_blankParam_shouldBeSkipped() {
        var filter =
                RsqlFilterBuilder.from(Map.of("status", "  ")).out("status").build();

        assertThat(filter).isEmpty();
    }

    // --- like ---

    @Test
    void like_sameField_shouldProduceWildcard() {
        var filter = RsqlFilterBuilder.from(Map.of("name", "lap")).like("name").build();

        assertThat(filter).isEqualTo("name==*lap*");
    }

    @Test
    void like_differentField_shouldMapToField() {
        var filter = RsqlFilterBuilder.from(Map.of("search", "lap"))
                .like("search", "name")
                .build();

        assertThat(filter).isEqualTo("name==*lap*");
    }

    @Test
    void like_missingParam_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of()).like("search", "name").build();

        assertThat(filter).isEmpty();
    }

    @Test
    void like_blankParam_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of("search", " "))
                .like("search", "name")
                .build();

        assertThat(filter).isEmpty();
    }

    // --- raw ---

    @Test
    void raw_shouldAppendClause() {
        var filter = RsqlFilterBuilder.from(Map.of()).raw("status==ACTIVE").build();

        assertThat(filter).isEqualTo("status==ACTIVE");
    }

    @Test
    void raw_null_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of()).raw(null).build();

        assertThat(filter).isEmpty();
    }

    @Test
    void raw_blank_shouldBeSkipped() {
        var filter = RsqlFilterBuilder.from(Map.of()).raw("  ").build();

        assertThat(filter).isEmpty();
    }

    // --- build: combining multiple clauses ---

    @Test
    void build_multipleClauses_shouldJoinWithSemicolon() {
        var filter = RsqlFilterBuilder.from(Map.of("status", "PAID", "minPrice", "100", "maxPrice", "500"))
                .eq("status")
                .gte("minPrice", "price")
                .lte("maxPrice", "price")
                .build();

        assertThat(filter).isEqualTo("status==PAID;price>=100;price<=500");
    }

    @Test
    void build_noClauses_shouldReturnEmpty() {
        var filter = RsqlFilterBuilder.from(Map.of()).build();

        assertThat(filter).isEmpty();
    }

    @Test
    void build_mixedPresentAndMissing_shouldOnlyIncludePresent() {
        var filter = RsqlFilterBuilder.from(Map.of("status", "ACTIVE"))
                .eq("status")
                .gte("minPrice", "price")
                .like("search", "name")
                .build();

        assertThat(filter).isEqualTo("status==ACTIVE");
    }

    // --- buildAndCombine ---

    @Test
    void buildAndCombine_withExistingFilter_shouldMergeBoth() {
        var filter = RsqlFilterBuilder.from(Map.of("status", "PAID"))
                .eq("status")
                .buildAndCombine("category.name==Electronics");

        assertThat(filter).isEqualTo("status==PAID;category.name==Electronics");
    }

    @Test
    void buildAndCombine_withNullExisting_shouldReturnBuilderOnly() {
        var filter =
                RsqlFilterBuilder.from(Map.of("status", "PAID")).eq("status").buildAndCombine(null);

        assertThat(filter).isEqualTo("status==PAID");
    }

    @Test
    void buildAndCombine_withBlankExisting_shouldReturnBuilderOnly() {
        var filter =
                RsqlFilterBuilder.from(Map.of("status", "PAID")).eq("status").buildAndCombine("  ");

        assertThat(filter).isEqualTo("status==PAID");
    }

    @Test
    void buildAndCombine_emptyBuilderWithExisting_shouldReturnExistingOnly() {
        var filter = RsqlFilterBuilder.from(Map.of()).buildAndCombine("status==ACTIVE");

        assertThat(filter).isEqualTo("status==ACTIVE");
    }

    @Test
    void buildAndCombine_bothEmpty_shouldReturnEmpty() {
        var filter = RsqlFilterBuilder.from(Map.of()).buildAndCombine("");

        assertThat(filter).isEmpty();
    }

    // --- Immutability: modifying original map should not affect builder ---

    @Test
    void from_shouldCopyParams() {
        var params = new java.util.HashMap<String, String>();
        params.put("status", "ACTIVE");
        var builder = RsqlFilterBuilder.from(params);
        params.put("status", "CHANGED");

        var filter = builder.eq("status").build();

        assertThat(filter).isEqualTo("status==ACTIVE");
    }

    // --- Full realistic scenario ---

    @Test
    void fullScenario_legacyOrderSearch() {
        var params = Map.of(
                "status", "PAID",
                "customer", "Dupont",
                "minAmount", "100",
                "maxAmount", "5000",
                "types", "STANDARD,EXPRESS",
                "search", "laptop");

        var filter = RsqlFilterBuilder.from(params)
                .eq("status")
                .eq("customer", "customer.name")
                .gte("minAmount", "amount")
                .lte("maxAmount", "amount")
                .in("types", "type")
                .like("search", "description")
                .build();

        assertThat(filter)
                .isEqualTo(
                        "status==PAID;customer.name==Dupont;amount>=100;amount<=5000;type=in=(STANDARD,EXPRESS);description==*laptop*");
    }
}
