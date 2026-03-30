package com.example.demo.dto.request;

import com.example.demo.model.ReadingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Partial update for a reading list entry — only non-null fields are applied")
public class UserBookUpdateDTO {

    /** New reading status. If set to READING, auto-sets startedAt. If COMPLETED, auto-sets completedAt. */
    @Schema(description = "New reading status (auto-sets timestamps)", example = "READING")
    private ReadingStatus status;

    /** Current page the user is on. Must be >= 0 if provided. */
    @Schema(description = "Current page number", example = "142")
    private Integer currentPage;

    /** Free-text notes about the book. */
    @Schema(description = "Free-text notes about the book", example = "Great chapter on dependency injection")
    private String notes;

}
