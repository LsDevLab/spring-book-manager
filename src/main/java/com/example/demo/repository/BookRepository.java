package com.example.demo.repository;

import com.example.demo.model.Book;
import com.example.demo.model.Topic;
import com.example.demo.model.UserBook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

// JpaRepository<Book, Long> — Spring Data auto-implements this interface at runtime.
// Provides: findAll(), findById(), save(), deleteById(), count(), etc. — zero SQL needed.
// No @Repository needed — JpaRepository is auto-detected by Spring Data.
public interface BookRepository extends JpaRepository<Book, UUID>, JpaSpecificationExecutor<Book> {

    /**
     * Paginated search filtered by topic.
     * Derived query: {@code WHERE topic = ? } with pagination applied.
     *
     * <p>Spring Data supports Pageable as a parameter on derived queries —
     * just add it as the last parameter and return Page<T>.</p>
     */
    Page<Book> findByTopic(Topic topic, Pageable pageable);

    /** Finds a book by its ISBN. Derived query: {@code WHERE isbn = ?}. */
    Optional<Book> findByIsbn(String isbn);

    /** Checks whether a book with the given ISBN exists. Derived query: {@code SELECT EXISTS(... WHERE isbn = ?)}. */
    boolean existsByIsbn(String isbn);

}
