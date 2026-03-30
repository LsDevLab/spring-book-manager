package com.example.demo.dto.request;

import com.example.demo.model.Book;
import com.example.demo.model.Topic;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO for incoming requests — no `id` field (server generates it).
// Decouples API contract from the JPA entity (see BookResponseDTO for the other direction).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for creating or updating a book")
public class BookRequestDTO {

    @NotBlank
    @Schema(description = "Book title", example = "Clean Code")
    private String title;

    @NotBlank
    @Schema(description = "Book author", example = "Robert C. Martin")
    private String author;

    @NotBlank
    @Pattern(regexp = "^[0-9]{3}-?[0-9]{1,5}-?[0-9]{1,7}-?[0-9]{1,7}-?[0-9]$",
            message = "ISBN must be a valid ISBN-13 format (e.g. 9780134685991)")
    @Schema(description = "ISBN-13 identifier", example = "978-0132350884")
    private String isbn;

    @NotNull
    @Schema(description = "Book topic/category", example = "BACKEND")
    private Topic topic;

    @NotNull
    @Min(1)
    @Max(10000)
    @Schema(description = "Total number of pages", example = "464")
    private Integer totalPages;

    // Manual conversion: DTO → Entity (alternative to MapStruct, see BookMapper.java)
    public Book toEntity() {
        Book book = new Book();
        book.setTitle(this.title);
        book.setAuthor(this.author);
        book.setIsbn(this.isbn);
        book.setTopic(this.topic);
        book.setTotalPages(this.totalPages);
        return book;
    }

}
