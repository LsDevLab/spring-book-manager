package com.example.demo.dto.request;

import com.example.demo.model.ReadingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for partial updates (PATCH) on a {@link com.example.demo.model.UserBook}.
 *
 * <p>All fields are nullable — only non-null fields are applied to the entity.
 * This is the key difference from a PUT DTO where all fields would be required.</p>
 *
 * @see com.example.demo.service.UserBookService#updateUserBook
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBookUpdateDTO {

    /** New reading status. If set to READING, auto-sets startedAt. If COMPLETED, auto-sets completedAt. */
    private ReadingStatus status;

    /** Current page the user is on. Must be >= 0 if provided. */
    private Integer currentPage;

    /** Free-text notes about the book. */
    private String notes;

}
