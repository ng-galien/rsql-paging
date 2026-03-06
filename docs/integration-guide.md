# Guide d'intégration par copie

Ce guide explique comment intégrer la lib rsql-paging dans un projet Spring Boot 3 existant en copiant directement les fichiers source.

## 1. Dépendance Maven

Ajouter la dépendance rsql-jpa-specification dans le `pom.xml` du projet cible :

```xml
<dependency>
    <groupId>io.github.perplexhub</groupId>
    <artifactId>rsql-jpa-spring-boot-starter</artifactId>
    <version>6.0.23</version>
</dependency>
```

Le projet doit déjà avoir `spring-boot-starter-data-jpa` et `spring-boot-starter-web`.

## 2. Copier les fichiers de la lib

Copier les 4 fichiers du package `com.rsqlpaging.lib` dans le projet cible, en adaptant le package :

```
src/main/java/com/rsqlpaging/lib/
├── RsqlPagingExecutor.java         # Le coeur — pagination 3 étapes
├── RsqlPageResult.java             # Record de résultat
├── RsqlPagingAutoConfiguration.java # Auto-configuration Spring Boot
└── RsqlPagingExceptionHandler.java  # Gestion d'erreurs → 400 ProblemDetail
```

### Adapter le package

Renommer le package dans les 4 fichiers. Par exemple, pour un projet `com.monprojet` :

```
com.rsqlpaging.lib → com.monprojet.rsqlpaging
```

### Enregistrer l'auto-configuration

Créer (ou compléter) le fichier :

```
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Avec :

```
com.monprojet.rsqlpaging.RsqlPagingAutoConfiguration
```

> **Alternative** : si le package est déjà scanné par `@SpringBootApplication`, on peut simplement ajouter `@Configuration` sur `RsqlPagingAutoConfiguration` et supprimer le fichier `AutoConfiguration.imports`.

## 3. Créer la méthode d'hydratation dans le repository

Pour chaque entité paginée, ajouter une méthode avec `LEFT JOIN FETCH` sur les associations lazy :

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.customer " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.id IN :ids")
    List<Order> findAllWithAssociationsByIdIn(@Param("ids") List<Long> ids);
}
```

**Points importants :**
- Toujours utiliser `LEFT JOIN FETCH` (pas `JOIN FETCH`) pour ne pas exclure les entités avec des relations nulles
- Fetcher toutes les associations qui seront sérialisées en JSON
- Si `open-in-view: false` (recommandé), toute association lazy non fetchée provoquera une `LazyInitializationException`

## 4. Utiliser dans un controller

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final RsqlPagingExecutor rsqlPagingExecutor;

    public OrderController(OrderRepository orderRepository, RsqlPagingExecutor rsqlPagingExecutor) {
        this.orderRepository = orderRepository;
        this.rsqlPagingExecutor = rsqlPagingExecutor;
    }

    @GetMapping
    public RsqlPageResult<Order> findOrders(
            @RequestParam(defaultValue = "") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Sort sort) {

        return rsqlPagingExecutor.findPage(
                orderRepository::findAllWithAssociationsByIdIn,
                Order.class,
                filter,
                sort,
                page,
                size
        );
    }
}
```

### Exemple d'appel

```
GET /api/orders?filter=customer.name==Dupont;status==PAID&sort=createdAt,desc&page=0&size=10
```

## 5. Comportements automatiques

| Comportement | Détail |
|---|---|
| **Sort par défaut** | Si aucun `sort` n'est fourni, tri automatique sur l'`@Id` de l'entité |
| **Validation du tri** | Les propriétés de tri sont vérifiées contre le metamodel JPA. Propriété inexistante → 400 |
| **Validation page/size** | `page < 0` ou `size < 1` → 400 |
| **RSQL invalide** | Filtre malformé → 400 avec message ProblemDetail |
| **Déduplication** | Les IDs dupliqués (joins dans le filtre RSQL) sont dédupliqués en Java |
| **Réordonnancement** | Les entités hydratées sont réordonnées pour respecter le tri demandé |

## 6. Configuration recommandée

```yaml
spring:
  jpa:
    open-in-view: false    # Obligatoire pour éviter les fuites de session
```

## 7. Limitations

- **Clés composites** : `@IdClass` et `@EmbeddedId` ne sont pas supportés. L'entité doit avoir un seul champ `@Id`.
- **Volume** : tous les IDs matchant le filtre sont chargés en mémoire. Adapté pour des tables jusqu'à quelques centaines de milliers de lignes. Au-delà, envisager du keyset pagination.
- **Collections fetchées** : si l'hydratation utilise `JOIN FETCH` sur une `@OneToMany`, Hibernate peut retourner des doublons. Utiliser `DISTINCT` dans la requête JPQL ou `@EntityGraph` à la place.

## Checklist d'intégration

- [ ] Dépendance `rsql-jpa-spring-boot-starter` ajoutée au pom.xml
- [ ] 4 fichiers de la lib copiés avec le bon package
- [ ] Auto-configuration enregistrée (imports ou @Configuration)
- [ ] Méthode d'hydratation avec `LEFT JOIN FETCH` dans le repository
- [ ] Controller utilisant `rsqlPagingExecutor.findPage(...)`
- [ ] `open-in-view: false` dans application.yml
