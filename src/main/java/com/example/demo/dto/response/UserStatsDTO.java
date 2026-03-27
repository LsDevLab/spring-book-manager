package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserStatsDTO {

    private long totalBooks;
    private long booksCompleted;
    private long booksReading;
    private long totalPagesRead;

}
