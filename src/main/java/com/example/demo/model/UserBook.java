package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Join entity between {@link User} and {@link Book} — represents a book on a user's reading list.
 *
 * <p>Unlike a plain {@code @ManyToMany}, this is modelled as its own entity because
 * the relationship carries extra data: reading status, progress, notes, and timestamps.</p>
 *
 * <p>Relationships:</p>
 * <ul>
 *   <li>{@code @ManyToOne User} — many UserBook rows can reference one User</li>
 *   <li>{@code @ManyToOne Book} — many UserBook rows can reference one Book</li>
 * </ul>
 *
 * <p>Introduced in Phase 3.2.</p>
 */
@Entity
@Table(name = "user_book")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    private ReadingStatus status;

    private Integer currentPage;

    private String notes;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;


}
