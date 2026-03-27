package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// @Entity — tells JPA "this class maps to a database table"
// @Table — explicit table name (otherwise defaults to class name "Book")
// @Data — Lombok: generates getters, setters, toString, equals, hashCode
// @NoArgsConstructor — JPA requires a no-arg constructor
// @AllArgsConstructor — convenient for creating instances with all fields
@Entity
@Table(name = "books")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    // @Id — marks this field as the primary key
    // @GeneratedValue UUID
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;

    private String author;

    private String isbn;

    // @Enumerated STRING — stores "BACKEND" in DB, not 0/1 ordinal (safer if enum reordered)
    @Enumerated(EnumType.STRING)
    private Topic topic;

    private Integer totalPages;

}
