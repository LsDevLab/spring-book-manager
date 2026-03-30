package com.example.demo.dto.response;

import com.example.demo.model.Book;
import com.example.demo.model.Topic;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// DTO for outgoing responses — controls exactly which fields the client sees.
// Includes `id` (unlike request DTO). No JPA annotations — this is not an entity.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Book details")
public class BookResponseDTO {

    @Schema(description = "Book ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Book title", example = "Clean Code")
    private String title;

    @Schema(description = "Book author", example = "Robert C. Martin")
    private String author;

    @Schema(description = "ISBN-13 identifier", example = "978-0132350884")
    private String isbn;

    @Schema(description = "Book topic/category", example = "BACKEND")
    private Topic topic;

    @Schema(description = "Total number of pages", example = "464")
    private Integer totalPages;

    // Manual conversion: Entity → DTO (alternative to MapStruct, see BookMapper.java)
    public static BookResponseDTO fromEntity(Book book) {
        return new BookResponseDTO(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getTopic(),
                book.getTotalPages()
        );
    }

}
