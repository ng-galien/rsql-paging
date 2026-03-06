# rsql-paging

Mini-lib Spring Boot 3 / Java 21 qui implémente une stratégie de paging **ID-first** avec filtrage [RSQL](https://github.com/perplexhub/rsql-jpa-specification).

## Le problème

Le paging classique avec `OFFSET` / `LIMIT` et `COUNT(*)` sur des requêtes avec joins complexes est lent et instable :
- `OFFSET` profond scanne toutes les lignes précédentes
- `COUNT(*)` re-exécute la requête complète
- Les joins multiplient les lignes et faussent le count

## La solution : paging en 3 étapes

1. **ID query** — Requête légère qui récupère uniquement les IDs correspondant au filtre RSQL, triés. Pas de `LIMIT`, pas de `JOIN` inutile — juste des longs en mémoire.
2. **Découpe en mémoire** — `count = ids.size()`. La page courante est un `subList(page * size, ...)`. Pas de `COUNT` en base, pas d'`OFFSET`.
3. **Hydratation** — Requête `WHERE id IN (...)` sur les IDs de la page courante. L'appelant fournit sa propre fonction d'hydratation (fetch joins, entity graphs, projections...).

### Trade-off

Tous les IDs sont chargés en mémoire à chaque requête. C'est négligeable en taille (des longs), mais implique que la requête ID parcourt l'ensemble du résultat filtré. Sur des volumétries classiques, c'est largement plus performant qu'un `OFFSET` profond ou qu'un `COUNT` séparé.

## Quickstart

### Prérequis

- Java 21+
- Maven
- Docker (pour PostgreSQL)

### Lancer la démo

```bash
docker compose up -d
mvn spring-boot:run
```

### Tester

```bash
# Paging simple
curl 'http://localhost:8080/api/products?page=0&size=3&sort=name,asc'

# Filtre RSQL
curl 'http://localhost:8080/api/products?filter=price>100&sort=price,desc'

# Filtre sur relation
curl 'http://localhost:8080/api/products?filter=category.name==Electronics&size=5'

# Combinaison AND
curl 'http://localhost:8080/api/products?filter=category.name==Electronics;price>500'
```

### Tests de charge (k6)

```bash
k6 run k6/paging-test.js
```

## API

### `RsqlPagingExecutor`

```java
// Hydratation simple (findAllById)
executor.findPage(repository, Product.class, filter, sort, page, size);

// Hydratation custom (recommandé — fetch joins, entity graphs...)
executor.findPage(
    productRepository::findAllWithCategoryByIdIn,
    Product.class,
    filter,
    sort,
    page,
    size
);
```

### `RsqlPageResult<T>`

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

### Syntaxe RSQL

| Opérateur | Signification |
|-----------|---------------|
| `==` | Égal |
| `!=` | Différent |
| `>`, `>=`, `<`, `<=` | Comparaison |
| `=in=(a,b,c)` | Dans la liste |
| `=out=(a,b,c)` | Pas dans la liste |
| `;` | AND |
| `,` | OR |
| `field.subfield` | Traversée de relation |

### Gestion d'erreurs

Toutes les erreurs d'input retournent un **400 Bad Request** au format [RFC 7807 ProblemDetail](https://www.rfc-editor.org/rfc/rfc7807) :
- Filtre RSQL invalide
- Propriété de tri inexistante
- Page négative ou size <= 0

## Intégration dans un projet existant

Voir le [guide d'intégration](docs/integration-guide.md).
