package com.rsqlpaging.lib;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestEntityRepository extends JpaRepository<TestEntity, Long> {

    @Query("SELECT e FROM TestEntity e LEFT JOIN FETCH e.category WHERE e.id IN :ids")
    List<TestEntity> findAllWithCategoryByIdIn(@Param("ids") List<Long> ids);
}
