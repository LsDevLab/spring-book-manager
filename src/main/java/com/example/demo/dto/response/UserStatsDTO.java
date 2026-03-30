package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "User's reading statistics")
public class UserStatsDTO {

    @Schema(description = "Total books in reading list", example = "12")
    private long totalBooks;

    @Schema(description = "Number of completed books", example = "5")
    private long booksCompleted;

    @Schema(description = "Number of books currently being read", example = "3")
    private long booksReading;

    @Schema(description = "Total pages read across all books", example = "2340")
    private long totalPagesRead;

}
