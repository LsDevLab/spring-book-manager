package com.example.demo.repository.projection;

/**
 * Projection interface for user reading stats — returned directly by a JPQL @Query.
 *
 * <p>Spring Data matches the getter names to the query's column aliases.
 * For example, {@code getTotalBooks()} maps to the alias {@code totalBooks} in the query.
 * No implementation needed — Spring generates a proxy at runtime.</p>
 *
 * <p>This is type-safe unlike the Object[] approach — you get named methods
 * instead of casting array indices.</p>
 */
public interface UserStatsProjection {

    long getTotalBooks();
    long getBooksCompleted();
    long getBooksReading();
    long getTotalPagesRead();
}
