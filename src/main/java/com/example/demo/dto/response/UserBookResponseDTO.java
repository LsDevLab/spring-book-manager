package com.example.demo.dto.response;

import com.example.demo.model.Book;
import com.example.demo.model.ReadingStatus;
import com.example.demo.model.UserBook;
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
public class UserBookResponseDTO {

    private ReadingStatus status;
    private Integer currentPage;
    private String notes;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
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