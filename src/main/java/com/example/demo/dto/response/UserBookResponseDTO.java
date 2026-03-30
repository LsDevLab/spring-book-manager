package com.example.demo.dto.response;

import com.example.demo.model.Book;
import com.example.demo.model.ReadingStatus;
import com.example.demo.model.UserBook;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for {@link com.example.demo.model.UserBook} — a user's reading list entry.
 *
 * <p>Nests the full {@link com.example.demo.model.Book} entity rather than flattening its fields,
 * so the client gets a clean {@code "book": {...}} structure in the JSON.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reading list entry with book details and reading progress")
public class UserBookResponseDTO {

    @Schema(description = "Current reading status", example = "READING")
    private ReadingStatus status;

    @Schema(description = "Current page the user is on", example = "142")
    private Integer currentPage;

    @Schema(description = "User's notes about the book", example = "Great chapter on dependency injection")
    private String notes;

    @Schema(description = "When the user started reading")
    private LocalDateTime startedAt;

    @Schema(description = "When the user finished reading")
    private LocalDateTime completedAt;

    @Schema(description = "The book details")
    private Book book;

    public static UserBookResponseDTO fromEntity(UserBook userBook) {
        return new UserBookResponseDTO(
                userBook.getStatus(),
                userBook.getCurrentPage(),
                userBook.getNotes(),
                userBook.getStartedAt(),
                userBook.getCompletedAt(),
                userBook.getBook()
        );
    }
}
