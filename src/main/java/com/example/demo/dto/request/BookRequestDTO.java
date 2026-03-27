package com.example.demo.dto.request;

import com.example.demo.model.Book;
import com.example.demo.model.Topic;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO for incoming requests — no `id` field (server generates it).
// Decouples API contract from the JPA entity (see BookResponseDTO for the other direction).
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookRequestDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String author;

    @NotBlank
    @Pattern(regexp = "^[0-9]{3}-?[0-9]{1,5}-?[0-9]{1,7}-?[0-9]{1,7}-?[0-9]$",
            message = "ISBN must be a valid ISBN-13 format (e.g. 9780134685991)")
    private String isbn;

    @NotNull
    private Topic topic;

    @NotNull
    @Min(1)
    @Max(10000)
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