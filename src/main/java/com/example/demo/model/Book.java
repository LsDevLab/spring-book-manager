package com.example.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Book entity representing a technical book")
public class Book {

    // @Id — marks this field as the primary key
    // @GeneratedValue UUID
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "Book ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Book title", example = "Clean Code")
    private String title;

    @Schema(description = "Book author", example = "Robert C. Martin")
    private String author;

    @Schema(description = "ISBN-13 identifier", example = "978-0132350884")
    private String isbn;

    // @Enumerated STRING — stores "BACKEND" in DB, not 0/1 ordinal (safer if enum reordered)
    @Enumerated(EnumType.STRING)
    @Schema(description = "Book topic/category", example = "BACKEND")
    private Topic topic;

    @Schema(description = "Total number of pages", example = "464")
    private Integer totalPages;

}
