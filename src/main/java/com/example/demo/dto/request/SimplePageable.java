package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// A simplified pagination request — only page and size, no sort.
// Use this instead of Spring's Pageable when the endpoint has fixed ordering
// (e.g., recommendations sorted by JPQL query, not by the client).
//
// Spring auto-binds query params to this via @ModelAttribute:
//   GET /api/books/recommendations?topic=BACKEND&page=0&size=10
//
// Compare with Spring's Pageable which also binds ?sort=title,desc — we don't want that here.
@Data
@Schema(description = "Pagination request (page and size only, no sort)")
public class SimplePageable {

    @Schema(description = "Page number (0-based)", example = "0")
    private int page = 0;

    @Schema(description = "Page size", example = "20")
    private int size = 20;

    // Converts to Spring's Pageable for use with JPA repositories.
    // No sort — ordering is determined by the query itself.
    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }
}
