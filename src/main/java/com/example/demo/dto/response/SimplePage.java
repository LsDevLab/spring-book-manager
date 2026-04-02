package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// A clean pagination wrapper that exposes only page/size info — no sort, pageable, empty, first, last noise.
//
// Why not just return Spring's Page<T>?
// Page<T> serializes ~15 fields (sort, pageable, empty, first, last, numberOfElements...).
// Most of them are derived or irrelevant to the client. This DTO keeps the response minimal.
//
// Generic <T> — works with any content type (BookResponseDTO, UserResponseDTO, etc.).
@Data
@AllArgsConstructor
@Schema(description = "Paginated response with minimal pagination metadata")
public class SimplePage<T> {

    @Schema(description = "Page content")
    private List<T> content;

    @Schema(description = "Current page number (0-based)", example = "0")
    private int page;

    @Schema(description = "Page size", example = "20")
    private int size;

    @Schema(description = "Total number of elements across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "3")
    private int totalPages;

    // Static factory method — converts Spring's Page<T> into our clean DTO.
    // Usage: SimplePage.from(page)
    public static <T> SimplePage<T> from(Page<T> page) {
        return new SimplePage<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    // Transforms content from type T to type R — same idea as Page.map().
    // Keeps pagination metadata intact, only converts the list items.
    // Usage: simplePage.map(BookResponseDTO::fromEntity)
    public <R> SimplePage<R> map(Function<T, R> converter) {
        List<R> convertedContent = content.stream().map(converter).toList();
        return new SimplePage<>(convertedContent, page, size, totalElements, totalPages);
    }
}
