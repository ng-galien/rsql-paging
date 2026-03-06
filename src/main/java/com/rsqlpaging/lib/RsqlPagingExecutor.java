package com.rsqlpaging.lib;

import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

public class RsqlPagingExecutor {

    private static final Logger log = LoggerFactory.getLogger(RsqlPagingExecutor.class);
    private static final int DEFAULT_MAX_ID_COUNT = 1_000_000;

    private final EntityManager entityManager;
    private final int maxIdCount;

    public RsqlPagingExecutor(EntityManager entityManager) {
        this(entityManager, DEFAULT_MAX_ID_COUNT);
    }

    public RsqlPagingExecutor(EntityManager entityManager, int maxIdCount) {
        this.entityManager = entityManager;
        this.maxIdCount = maxIdCount;
    }

    /** Paging with default hydration (findAllById). */
    public <T, ID> RsqlPageResult<T> findPage(
            JpaRepository<T, ID> repository, Class<T> entityClass, String rsqlFilter, Sort sort, int page, int size) {

        return findPage(repository::findAllById, entityClass, rsqlFilter, sort, page, size);
    }

    /**
     * Paging with custom hydration (fetch joins, entity graphs, projections...).
     *
     * @param hydrator function that loads entities from a list of IDs
     */
    public <T, ID> RsqlPageResult<T> findPage(
            Function<List<ID>, List<T>> hydrator,
            Class<T> entityClass,
            String rsqlFilter,
            Sort sort,
            int page,
            int size) {

        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1, got: " + size);
        }

        var idFieldName = resolveIdFieldName(entityClass);

        // Fallback sort on ID to guarantee deterministic ordering
        var effectiveSort = (sort == null || sort.isUnsorted()) ? Sort.by(idFieldName) : sort;
        validateSortProperties(entityClass, effectiveSort);

        // Step 1 — ID query
        List<ID> allIds = fetchIds(entityClass, rsqlFilter, effectiveSort);

        // Step 2 — In-memory slicing (long arithmetic to avoid overflow)
        var total = allIds.size();
        var fromIndex = (int) Math.min((long) page * size, total);
        var toIndex = Math.min(fromIndex + size, total);
        var pageIds = allIds.subList(fromIndex, toIndex);

        if (pageIds.isEmpty()) {
            return RsqlPageResult.empty(page, size, total);
        }

        // Step 3 — Hydration
        var entities = hydrator.apply(pageIds);
        var ordered = reorder(entities, pageIds);

        if (ordered.size() < pageIds.size()) {
            log.warn(
                    "Hydration returned {} entities for {} requested IDs — possible concurrent deletion",
                    ordered.size(),
                    pageIds.size());
        }

        return RsqlPageResult.of(ordered, page, size, total);
    }

    private <T> String resolveIdFieldName(Class<T> entityClass) {
        var entityType = entityManager.getMetamodel().entity(entityClass);
        if (!entityType.hasSingleIdAttribute()) {
            throw new UnsupportedOperationException(
                    "Composite keys (@IdClass/@EmbeddedId) are not supported. Entity: " + entityClass.getName());
        }
        return entityType.getId(entityType.getIdType().getJavaType()).getName();
    }

    private <T> void validateSortProperties(Class<T> entityClass, Sort sort) {
        var entityType = entityManager.getMetamodel().entity(entityClass);
        var attributeNames = entityType.getSingularAttributes().stream()
                .map(SingularAttribute::getName)
                .collect(Collectors.toUnmodifiableSet());

        sort.forEach(order -> {
            var property = order.getProperty();
            var rootProperty = property.contains(".") ? property.substring(0, property.indexOf('.')) : property;
            if (!attributeNames.contains(rootProperty)) {
                throw new IllegalArgumentException(
                        "Invalid sort property: '%s'. Available properties: %s".formatted(property, attributeNames));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T, ID> List<ID> fetchIds(Class<T> entityClass, String rsqlFilter, Sort sort) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Object.class);
        var root = query.from(entityClass);

        query.select(root.get(resolveIdFieldName(entityClass)));

        // Apply RSQL filter
        if (rsqlFilter != null && !rsqlFilter.isBlank()) {
            Specification<T> spec = RSQLJPASupport.toSpecification(rsqlFilter);
            query.where(spec.toPredicate(root, query, cb));
        }

        // Apply sorting — sort is always present thanks to the fallback in findPage
        var orders = sort.stream()
                .map(order -> order.isAscending()
                        ? cb.asc(resolvePath(root, order.getProperty()))
                        : cb.desc(resolvePath(root, order.getProperty())))
                .toList();
        query.orderBy(orders);

        // Hard limit to prevent OOM on large tables
        var jpaQuery = entityManager.createQuery(query);
        jpaQuery.setMaxResults(maxIdCount + 1);
        var raw = jpaQuery.getResultList();

        // Java-side deduplication (DISTINCT + ORDER BY on non-selected columns is problematic in
        // SQL)
        var deduped = new LinkedHashSet<>(raw).stream().toList();

        if (deduped.size() > maxIdCount) {
            throw new IllegalStateException(
                    "Query returned more than %d IDs. Narrow your filter or increase rsql.paging.max-id-count."
                            .formatted(maxIdCount));
        }

        return (List<ID>) (List<?>) deduped;
    }

    private <T> Path<?> resolvePath(Root<T> root, String property) {
        Path<?> path = root;
        for (var segment : property.split("\\.")) {
            path = path.get(segment);
        }
        return path;
    }

    private <T, ID> List<T> reorder(List<T> entities, List<ID> orderedIds) {
        var util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        var byId = entities.stream()
                .collect(Collectors.toMap(util::getIdentifier, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
