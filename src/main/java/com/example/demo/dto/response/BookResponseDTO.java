package com.example.demo.dto.response;

import com.example.demo.model.Book;
import com.example.demo.model.Topic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// DTO for outgoing responses — controls exactly which fields the client sees.
// Includes `id` (unlike request DTO). No JPA annotations — this is not an entity.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookResponseDTO {

    private UUID id;

    private String title;

    private String author;

    private String isbn;

    private Topic topic;

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
