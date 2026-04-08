package com.example.demo.repository;

import com.example.demo.model.Book;
import com.example.demo.model.ReadingStatus;
import com.example.demo.model.Topic;
import com.example.demo.model.UserBook;
import com.example.demo.repository.projection.UserStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link UserBook} join entities.
 *
 * <p>Includes derived query methods — Spring generates the SQL from the method name at startup.</p>
 */
public interface UserBookRepository extends JpaRepository<UserBook, UUID> {

    /** Find all reading list entries for a given user. Derived query: {@code WHERE user_id = ?}. */
    List<UserBook> findByUserId(UUID userId);

    /** Paginated reading list entries for a user. Derived query: {@code WHERE user_id = ?} with pagination. */
    Page<UserBook> findByUserId(UUID userId, Pageable pageable);

    /** Find a specific user-book entry. Derived query: {@code WHERE user_id = ? AND book_id = ?}. */
    Optional<UserBook> findByUserIdAndBookId(UUID userId, UUID bookId);

    /** Checks whether a user already has a specific book in their reading list. */
    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    /** Count all books on a user's reading list. Derived: {@code SELECT COUNT(*) WHERE user_id = ?}. */
    long countByUserId(UUID userId);

    /** Count books with a specific status. Derived: {@code WHERE user_id = ? AND status = ?}. */
    long countByUserIdAndStatus(UUID userId, ReadingStatus status);

    /** Count all reading list entries with a given status across all users. */
    long countByStatus(ReadingStatus status);

    /**
     * Aggregated reading stats for a user in a single DB round-trip.
     * Uses CASE WHEN to count by status and COALESCE for null-safe page summing.
     */
    @Query("""
                SELECT COUNT(ub) AS totalBooks,
                       SUM(CASE WHEN ub.status = 'COMPLETED' THEN 1 ELSE 0 END) AS booksCompleted,
                       SUM(CASE WHEN ub.status = 'READING' THEN 1 ELSE 0 END) AS booksReading,
                       COALESCE(SUM(ub.currentPage), 0) AS totalPagesRead
                FROM UserBook ub
                WHERE ub.user.id = :userId
            """)
    UserStatsProjection getUserStats(@Param("userId") UUID userId);

    /**
     * Returns books for a topic ordered by how many users have them in their reading list (most read first).
     * Uses a JOIN + GROUP BY + COUNT aggregation with pagination.
     */
    @Query("""
                SELECT b FROM Book b
                JOIN UserBook ub ON ub.book = b
                WHERE b.topic = :topic
                GROUP BY b
                ORDER BY COUNT(ub) DESC
            """)
    Page<Book> getMostReadBookByTopic(@Param("topic") Topic topic, Pageable pageable);

}
