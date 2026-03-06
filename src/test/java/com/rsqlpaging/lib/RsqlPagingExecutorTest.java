package com.rsqlpaging.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = RsqlPagingExecutorTest.TestConfig.class)
@ActiveProfiles("test")
@Transactional
class RsqlPagingExecutorTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TestEntity.class)
    @EnableJpaRepositories(basePackageClasses = TestEntityRepository.class)
    static class TestConfig {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestEntityRepository testEntityRepository;

    @Autowired
    private TestCategoryRepository testCategoryRepository;

    private RsqlPagingExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new RsqlPagingExecutor(entityManager);

        testEntityRepository.deleteAll();
        testCategoryRepository.deleteAll();

        var catA = testCategoryRepository.save(new TestCategory("Electronics"));
        var catB = testCategoryRepository.save(new TestCategory("Books"));

        testEntityRepository.save(new TestEntity("Laptop", 1200, catA));
        testEntityRepository.save(new TestEntity("Phone", 800, catA));
        testEntityRepository.save(new TestEntity("Tablet", 500, catA));
        testEntityRepository.save(new TestEntity("Novel", 15, catB));
        testEntityRepository.save(new TestEntity("Textbook", 60, catB));

        entityManager.flush();
        entityManager.clear();
    }

    // --- findPage with JpaRepository (default hydration) ---

    @Test
    void findPage_defaultHydration_shouldReturnFirstPage() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 0, 3);

        assertThat(result.content()).hasSize(3);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isFalse();
        assertThat(result.content().stream().map(TestEntity::getName).toList())
                .containsExactly("Laptop", "Novel", "Phone");
    }

    @Test
    void findPage_defaultHydration_shouldReturnSecondPage() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 1, 3);

        assertThat(result.content()).hasSize(2);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isTrue();
        assertThat(result.content().stream().map(TestEntity::getName).toList()).containsExactly("Tablet", "Textbook");
    }

    // --- findPage with custom hydrator ---

    @Test
    void findPage_customHydrator_shouldReturnHydratedEntities() {
        var result = executor.findPage(
                testEntityRepository::findAllWithCategoryByIdIn, TestEntity.class, "", Sort.by("name"), 0, 3);

        assertThat(result.content()).hasSize(3);
        assertThat(result.content().get(0).getCategory()).isNotNull();
        assertThat(result.content().get(0).getCategory().getName()).isNotNull();
    }

    // --- RSQL filter ---

    @Test
    void findPage_withRsqlFilter_shouldFilterResults() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "price>100", Sort.by("name"), 0, 10);

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content().stream().map(TestEntity::getName).toList())
                .containsExactly("Laptop", "Phone", "Tablet");
    }

    @Test
    void findPage_withRsqlFilterOnRelation_shouldWork() {
        var result = executor.findPage(
                testEntityRepository::findAllWithCategoryByIdIn,
                TestEntity.class,
                "category.name==Books",
                Sort.by("name"),
                0,
                10);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content().stream().map(TestEntity::getName).toList()).containsExactly("Novel", "Textbook");
    }

    @Test
    void findPage_withEmptyFilter_shouldReturnAll() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 0, 10);
        assertThat(result.totalElements()).isEqualTo(5);
    }

    @Test
    void findPage_withNullFilter_shouldReturnAll() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, null, Sort.by("name"), 0, 10);
        assertThat(result.totalElements()).isEqualTo(5);
    }

    @Test
    void findPage_withBlankFilter_shouldReturnAll() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "   ", Sort.by("name"), 0, 10);
        assertThat(result.totalElements()).isEqualTo(5);
    }

    // --- Sort ---

    @Test
    void findPage_withDescSort_shouldReverseOrder() {
        var result = executor.findPage(
                testEntityRepository, TestEntity.class, "", Sort.by(Sort.Direction.DESC, "name"), 0, 5);

        assertThat(result.content().stream().map(TestEntity::getName).toList())
                .containsExactly("Textbook", "Tablet", "Phone", "Novel", "Laptop");
    }

    @Test
    void findPage_withNullSort_shouldFallbackToIdSort() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "", null, 0, 10);

        assertThat(result.totalElements()).isEqualTo(5);
        // IDs are in insertion order, so content should be ordered by ID
        var ids = result.content().stream().map(TestEntity::getId).toList();
        assertThat(ids).isSorted();
    }

    @Test
    void findPage_withUnsortedSort_shouldFallbackToIdSort() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "", Sort.unsorted(), 0, 10);

        var ids = result.content().stream().map(TestEntity::getId).toList();
        assertThat(ids).isSorted();
    }

    @Test
    void findPage_withInvalidSortProperty_shouldThrow() {
        assertThatThrownBy(() ->
                        executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("nonExistent"), 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort property")
                .hasMessageContaining("nonExistent");
    }

    @Test
    void findPage_withNestedSortProperty_shouldValidateRootOnly() {
        // category.name — root "category" is a valid attribute
        var result = executor.findPage(
                testEntityRepository::findAllWithCategoryByIdIn, TestEntity.class, "", Sort.by("category.name"), 0, 10);

        assertThat(result.totalElements()).isEqualTo(5);
    }

    // --- Input validation ---

    @Test
    void findPage_withNegativePage_shouldThrow() {
        assertThatThrownBy(() -> executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), -1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must be >= 0");
    }

    @Test
    void findPage_withZeroSize_shouldThrow() {
        assertThatThrownBy(() -> executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be >= 1");
    }

    @Test
    void findPage_withNegativeSize_shouldThrow() {
        assertThatThrownBy(() -> executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be >= 1");
    }

    // --- Empty results ---

    @Test
    void findPage_withNoMatchingFilter_shouldReturnEmpty() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "price>99999", Sort.by("name"), 0, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }

    @Test
    void findPage_withPageBeyondResults_shouldReturnEmpty() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 100, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(5);
    }

    // --- Hard limit ---

    @Test
    void findPage_exceedingMaxIdCount_shouldThrow() {
        var limitedExecutor = new RsqlPagingExecutor(entityManager, 3);

        assertThatThrownBy(() ->
                        limitedExecutor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 0, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than 3 IDs");
    }

    @Test
    void findPage_atExactMaxIdCount_shouldSucceed() {
        var limitedExecutor = new RsqlPagingExecutor(entityManager, 5);

        var result = limitedExecutor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 0, 10);

        assertThat(result.totalElements()).isEqualTo(5);
    }

    // --- Composite key rejection ---

    @Test
    void findPage_withCompositeKey_shouldThrow() {
        assertThatThrownBy(() -> executor.findPage(
                        ids -> List.of(), CompositeKeyEntity.class, "", Sort.by("description"), 0, 10))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Composite keys");
    }

    // --- RSQL parse error ---

    @Test
    void findPage_withInvalidRsql_shouldThrow() {
        assertThatThrownBy(() ->
                        executor.findPage(testEntityRepository, TestEntity.class, "===invalid", Sort.by("name"), 0, 10))
                .isInstanceOf(cz.jirutka.rsql.parser.RSQLParserException.class);
    }

    // --- Reorder preserves sort order ---

    @Test
    void findPage_shouldPreserveRequestedOrder() {
        var result = executor.findPage(
                testEntityRepository::findAllWithCategoryByIdIn,
                TestEntity.class,
                "",
                Sort.by(Sort.Direction.ASC, "price"),
                0,
                5);

        var prices = result.content().stream().map(TestEntity::getPrice).toList();
        assertThat(prices).isSorted();
    }

    // --- Default constructor ---

    @Test
    void defaultConstructor_shouldUseDefaultMaxIdCount() {
        var defaultExecutor = new RsqlPagingExecutor(entityManager);

        var result = defaultExecutor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 0, 10);

        assertThat(result.totalElements()).isEqualTo(5);
    }

    // --- Hydration mismatch (concurrent deletion scenario) ---

    @Test
    void findPage_hydrationReturningFewerEntities_shouldStillWork() {
        var result = executor.<TestEntity, Long>findPage(
                ids -> ids.stream()
                        .limit(ids.size() - 1L)
                        .map(id -> testEntityRepository.findById(id).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                TestEntity.class,
                "",
                Sort.by("name"),
                0,
                5);

        assertThat(result.content()).hasSize(4);
        assertThat(result.totalElements()).isEqualTo(5);
    }

    // --- Multiple sort orders ---

    @Test
    void findPage_withMultipleSortOrders_shouldWork() {
        var result = executor.findPage(
                testEntityRepository,
                TestEntity.class,
                "",
                Sort.by(Sort.Order.asc("price"), Sort.Order.desc("name")),
                0,
                10);

        assertThat(result.content()).hasSize(5);
        var prices = result.content().stream().map(TestEntity::getPrice).toList();
        assertThat(prices).isSorted();
    }

    // --- AND / OR RSQL filters ---

    @Test
    void findPage_withAndRsqlFilter_shouldFilterResults() {
        var result = executor.findPage(
                testEntityRepository, TestEntity.class, "price>100;name==Laptop", Sort.by("name"), 0, 10);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).getName()).isEqualTo("Laptop");
    }

    @Test
    void findPage_withOrRsqlFilter_shouldFilterResults() {
        var result = executor.findPage(
                testEntityRepository, TestEntity.class, "name==Laptop,name==Novel", Sort.by("name"), 0, 10);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content().stream().map(TestEntity::getName).toList()).containsExactly("Laptop", "Novel");
    }

    // --- Edge case: page size of 1 ---

    @Test
    void findPage_withSizeOne_shouldPaginateCorrectly() {
        var result = executor.findPage(testEntityRepository, TestEntity.class, "", Sort.by("name"), 2, 1);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getName()).isEqualTo("Phone");
        assertThat(result.totalPages()).isEqualTo(5);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isFalse();
    }

    // --- Hydrator returning duplicate entities should keep first occurrence ---

    @Test
    void findPage_hydrationReturningDuplicates_shouldKeepFirst() {
        var result = executor.<TestEntity, Long>findPage(
                ids -> {
                    var entities = testEntityRepository.findAllById(ids);
                    // Return duplicates by adding the list to itself
                    var doubled = new java.util.ArrayList<>(entities);
                    doubled.addAll(entities);
                    return doubled;
                },
                TestEntity.class,
                "",
                Sort.by("name"),
                0,
                5);

        assertThat(result.content()).hasSize(5);
        assertThat(result.content().stream().map(TestEntity::getName).toList())
                .containsExactly("Laptop", "Novel", "Phone", "Tablet", "Textbook");
    }
}
