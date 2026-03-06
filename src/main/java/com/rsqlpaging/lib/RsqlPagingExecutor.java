package com.rsqlpaging.lib;

import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Component
public class RsqlPagingExecutor {

    private final EntityManager entityManager;

    public RsqlPagingExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Paging avec hydratation par défaut (findAllById).
     */
    public <T, ID> RsqlPageResult<T> findPage(
            JpaRepository<T, ID> repository,
            Class<T> entityClass,
            String rsqlFilter,
            Sort sort,
            int page,
            int size) {

        return findPage(repository::findAllById, entityClass, rsqlFilter, sort, page, size);
    }

    /**
     * Paging avec hydratation custom (fetch joins, entity graphs, projections...).
     *
     * @param hydrator  fonction qui charge les entités à partir d'une liste d'IDs
     */
    public <T, ID> RsqlPageResult<T> findPage(
            Function<List<ID>, List<T>> hydrator,
            Class<T> entityClass,
            String rsqlFilter,
            Sort sort,
            int page,
            int size) {

        // Step 1 — ID query
        List<ID> allIds = fetchIds(entityClass, rsqlFilter, sort);

        // Step 2 — Découpe en mémoire
        int total = allIds.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<ID> pageIds = allIds.subList(fromIndex, toIndex);

        if (pageIds.isEmpty()) {
            return RsqlPageResult.empty(page, size, total);
        }

        // Step 3 — Hydratation
        List<T> entities = hydrator.apply(pageIds);
        List<T> ordered = reorder(entities, pageIds);

        return RsqlPageResult.of(ordered, page, size, total);
    }

    @SuppressWarnings("unchecked")
    private <T, ID> List<ID> fetchIds(Class<T> entityClass, String rsqlFilter, Sort sort) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<T> root = query.from(entityClass);

        // Résolution dynamique de l'attribut @Id via le metamodel JPA
        EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);
        String idFieldName = entityType.getId(entityType.getIdType().getJavaType()).getName();
        query.select(root.get(idFieldName));

        // Application du filtre RSQL
        if (rsqlFilter != null && !rsqlFilter.isBlank()) {
            Specification<T> spec = RSQLJPASupport.toSpecification(rsqlFilter);
            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }

        // Application du tri
        if (sort != null && sort.isSorted()) {
            List<Order> orders = sort.stream()
                    .map(order -> order.isAscending()
                            ? cb.asc(root.get(order.getProperty()))
                            : cb.desc(root.get(order.getProperty())))
                    .toList();
            query.orderBy(orders);
        }

        // Déduplication en Java (DISTINCT + ORDER BY sur colonnes non-sélectionnées pose problème en SQL)
        List<Object> raw = entityManager.createQuery(query).getResultList();
        return (List<ID>) (List<?>) new LinkedHashSet<>(raw).stream().toList();
    }

    private <T, ID> List<T> reorder(List<T> entities, List<ID> orderedIds) {
        PersistenceUnitUtil util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        Map<Object, T> byId = new LinkedHashMap<>();
        for (T entity : entities) {
            byId.put(util.getIdentifier(entity), entity);
        }
        return orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
