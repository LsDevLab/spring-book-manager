package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.model.UserBook;
import com.example.demo.service.BookService;
import com.example.demo.service.UserBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Security: same ownership rule as REST UserBookController —
// admin can access anyone's reading list, regular users only their own.
@Controller
@RequiredArgsConstructor
public class UserBookGraphqlController {

    private final UserBookService userBookService;
    private final BookService bookService;

    // Uses getReadingListLazy — both user and book are LAZY (no @EntityGraph).
    // If the client doesn't ask for "book { ... }", no book query is ever fired.
    // If they do, @BatchMapping below handles it efficiently.
    @QueryMapping
    @PreAuthorize("hasRole('ADMIN') or authentication.details.userId.equals(#userId)")
    public List<UserBook> userBooks(@Argument UUID userId) {
        return userBookService.getReadingListLazy(userId);
    }

    // @BatchMapping — the GraphQL-native solution to N+1.
    //
    // Without this: if the client requests "book { title }" on 10 UserBooks,
    // Hibernate would fire 10 separate SELECTs (one per lazy book).
    //
    // With @BatchMapping: Spring GraphQL collects ALL UserBooks that need a "book"
    // field, passes them here as a list, and we load all books in ONE query.
    //
    // How it works:
    //   1. Client queries: { userBooks(userId: "...") { status book { title } } }
    //   2. Spring resolves userBooks → gets List<UserBook> (books not loaded yet — LAZY)
    //   3. Spring sees "book" is requested → calls this method with all UserBooks at once
    //   4. We extract all bookIds, load them in one query, and map them back
    //
    // The return type Map<UserBook, Book> tells Spring which Book belongs to which UserBook.
    @BatchMapping
    public Map<UserBook, Book> book(List<UserBook> userBooks) {
        // Collect all unique book IDs from the UserBooks
        List<UUID> bookIds = userBooks.stream()
                .map(ub -> ub.getBook().getId())
                // getBook() on a LAZY proxy only accesses the ID — Hibernate knows the FK
                // without loading the entity. No extra query here.
                .distinct()
                .toList();

        // One query: SELECT * FROM books WHERE id IN (?, ?, ...)
        Map<UUID, Book> booksById = bookService.getBooksByIds(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));

        // Map each UserBook to its Book
        return userBooks.stream()
                .collect(Collectors.toMap(ub -> ub, ub -> booksById.get(ub.getBook().getId())));
    }
}
