package com.example.demo.service;

import com.example.demo.dto.request.UserBookUpdateDTO;
import com.example.demo.dto.response.UserStatsDTO;
import com.example.demo.event.BookCompletedEvent;
import com.example.demo.exception.BookNotFoundException;
import com.example.demo.exception.UserBookNotFoundException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.*;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.UserBookRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.projection.UserStatsProjection;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for reading list operations — coordinates across User, Book, and UserBook.
 *
 * <p>This is the first service in the project that injects multiple repositories,
 * demonstrating how a service can orchestrate operations across several entities.</p>
 *
 * <p>Methods that modify data are annotated with {@code @Transactional} to ensure
 * atomicity — if any step fails, all DB changes in that method roll back.</p>
 */
@Service
@RequiredArgsConstructor
public class UserBookService {

    private final UserBookRepository userBookRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    @Resource
    private CacheManager redisCacheManager;

    /**
     * Returns all reading list entries for a user.
     *
     * @param userId the user's ID
     * @return list of UserBook entries (may be empty)
     * @throws UserNotFoundException if the user does not exist
     */
    public List<UserBook> getReadingList(UUID userId) {
        if(!userRepository.existsById(userId)){
            throw new UserNotFoundException(userId);
        }
        return userBookRepository.findByUserId(userId);
    }

    public Page<UserBook> searchReadingList(UUID userId, Pageable pageable) {
        if(!userRepository.existsById(userId)){
            throw new UserNotFoundException(userId);
        }
        return userBookRepository.findByUserId(userId, pageable);
    }

    /**
     * Adds a book to a user's reading list with initial status {@code WANT_TO_READ}.
     *
     * @param userId the user's ID
     * @param bookId the book's ID
     * @return the newly created UserBook entry
     * @throws UserNotFoundException if the user does not exist
     * @throws BookNotFoundException if the book does not exist
     */
    @Transactional
    public UserBook addToReadingList(UUID userId, UUID bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        UserBook newUserBook = new UserBook();
        newUserBook.setUser(user);
        newUserBook.setBook(book);
        newUserBook.setStatus(ReadingStatus.WANT_TO_READ);
        newUserBook.setCurrentPage(0);

        // manual evict
        Cache cache = redisCacheManager.getCache("recommendations");
        if (cache != null) {
            cache.clear();  // clears all entries in the "recommendations" cache
        }

        return userBookRepository.save(newUserBook);
    }

    /**
     * Removes a book from a user's reading list.
     *
     * @throws UserBookNotFoundException if the user-book entry does not exist
     */
    @Transactional
    @CacheEvict(value = "recommendations", allEntries = true, cacheManager = "redisCacheManager")
    public void removeFromReadingList(UUID userId, UUID bookId) {
        UserBook userBook = userBookRepository.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new UserBookNotFoundException(userId, bookId));
        userBookRepository.delete(userBook);
    }

    /**
     * Partially updates a reading list entry (PATCH semantics).
     *
     * <p>Only non-null fields in the DTO are applied. Status changes auto-set timestamps:
     * READING sets {@code startedAt} (if not already set), COMPLETED sets {@code completedAt}.</p>
     *
     * @throws UserBookNotFoundException if the user-book entry does not exist
     */
    @Transactional
    public UserBook updateUserBook(UUID userId, UUID bookId, UserBookUpdateDTO dto) {
        UserBook userBook = userBookRepository.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new UserBookNotFoundException(userId, bookId));

        // Only update fields that were actually sent (not null)
        if (dto.getStatus() != null) {

            // Auto-set timestamps based on status changes
            if (dto.getStatus() == ReadingStatus.READING && userBook.getStartedAt() == null) {
                userBook.setStartedAt(LocalDateTime.now());
            }
            if (dto.getStatus() == ReadingStatus.COMPLETED) {
                if(!userBook.getStatus().equals(ReadingStatus.COMPLETED)){
                    applicationEventPublisher.publishEvent(
                            new BookCompletedEvent(userId, bookId, userBook.getBook().getTitle(), userBook.getUser().getUsername())
                    );
                }
                userBook.setCompletedAt(LocalDateTime.now());
            }

            userBook.setStatus(dto.getStatus());
        }
        if (dto.getCurrentPage() != null) {
            userBook.setCurrentPage(dto.getCurrentPage());
        }
        if (dto.getNotes() != null) {
            userBook.setNotes(dto.getNotes());
        }


        return userBookRepository.save(userBook);
    }

    /**
     * Computes reading stats for a user using derived query methods.
     *
     * @param userId the user's ID
     * @return stats DTO with counts and total pages read
     * @throws UserNotFoundException if the user does not exist
     */
//    public UserStatsDTO getUserStats(UUID userId) {
//        if(!userBookRepository.existsById(userId)){
//            throw new UserNotFoundException(userId);
//        }
//        return new UserStatsDTO(
//            userBookRepository.countByUserId(userId),
//            userBookRepository.countByUserIdAndStatus(userId, ReadingStatus.COMPLETED),
//            userBookRepository.countByUserIdAndStatus(userId, ReadingStatus.READING),
//            userBookRepository.findByUserId(userId).stream()
//                    .mapToInt(ub -> Optional.of(ub.getCurrentPage()).orElse(0))
//                    .sum()
//        );
//    }

    /**
     * Computes reading stats using a single JPQL @Query aggregation.
     *
     * <p>Previous approach (3.4) used 4 separate queries + Java-side summing.
     * This approach does it all in one DB round-trip — the database handles
     * COUNT, SUM, and conditional logic via CASE WHEN.</p>
     *
     * @param userId the user's ID
     * @return stats DTO with counts and total pages read
     * @throws UserNotFoundException if the user does not exist
     */
    public UserStatsDTO getUserStats(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        UserStatsProjection stats = userBookRepository.getUserStats(userId);

        return new UserStatsDTO(
                stats.getTotalBooks(),       // totalBooks
                stats.getBooksCompleted(),       // booksCompleted
                stats.getBooksReading(),       // booksReading
                stats.getTotalPagesRead()     // totalPagesRead — SUM returns long in JPQL
        );
    }

    @Cacheable(value = "recommendations", key = "#topic + '-' + #pageable.pageNumber + '-' + #pageable.pageSize", cacheManager = "redisCacheManager")
    public Page<Book> getMostReadBookByTopic(Topic topic, Pageable pageable){
        return userBookRepository.getMostReadBookByTopic(topic, pageable);
    }


    public long countByStatus(ReadingStatus status){
        return userBookRepository.countByStatus(status);
    }


}
