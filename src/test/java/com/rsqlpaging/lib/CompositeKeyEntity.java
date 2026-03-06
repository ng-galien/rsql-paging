package com.rsqlpaging.lib;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "composite_key_entity")
@IdClass(CompositeKeyEntity.CompositeKey.class)
public class CompositeKeyEntity {

    @Id
    private Long partA;

    @Id
    private Long partB;

    private String description;

    public static class CompositeKey implements Serializable {
        private Long partA;
        private Long partB;
    }
}
