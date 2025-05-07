package com.siemens.internship.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing an Item.
 *
 * Note: all columns are non-null at the DB level where DTO enforces mandatory fields.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /** Name (max 100 chars); enforced to be non-null by validation layer. */
    @Column(nullable = false, length = 100)
    private String name;

    /** Optional description (max 255 chars). */
    @Column(length = 255)
    private String description;

    /** Status field (max 20 chars); non-null. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Unique user email (max 120 chars); non-null. */
    @Column(nullable = false, unique = true, length = 120)
    private String email;
}
