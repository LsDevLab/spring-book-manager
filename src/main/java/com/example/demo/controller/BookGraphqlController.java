package com.example.demo.controller;

import com.example.demo.dto.BookMapper;
import com.example.demo.dto.request.BookRequestDTO;
import com.example.demo.model.Book;
import com.example.demo.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

// @Controller, NOT @RestController!
// In GraphQL, the framework handles JSON serialization — you just return Java objects.
// @RestController would add @ResponseBody which conflicts with GraphQL's own serialization.
//
// Security: queries are open to any authenticated user (JWT required via SecurityFilterChain).
// Mutations require ADMIN role — same rules as the REST BookController.
// @PreAuthorize works identically on GraphQL @Controller methods because Spring Security
// checks the SecurityContext regardless of whether the request came from REST or GraphQL.
@Controller
@RequiredArgsConstructor
public class BookGraphqlController {

    private final BookService bookService;
    private final BookMapper bookMapper;

    // Method name "books" matches the schema: books: [Book!]!
    @QueryMapping
    public List<Book> books() {
        return bookService.getAllBooks();
    }

    // Method name "book" matches the schema: book(id: ID!): Book
    // @Argument maps to the GraphQL argument — like @PathVariable for REST
    @QueryMapping
    public Book book(@Argument UUID id) {
        return bookService.getBookById(id).orElse(null);
        // Returns null when not found — GraphQL handles this gracefully
        // because the schema says Book (nullable), not Book!
    }

    // Method name "createBook" matches the schema: createBook(input: BookInput!): Book!
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Book createBook(@Argument BookRequestDTO input) {
        Book book = bookMapper.toEntity(input);
        return bookService.createBook(book);
    }

    // Method name "updateBook" matches: updateBook(id: ID!, input: BookInput!): Book!
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Book updateBook(@Argument UUID id, @Argument BookRequestDTO input) {
        Book book = bookMapper.toEntity(input);
        return bookService.updateBook(id, book);
    }

    // Method name "deleteBook" matches: deleteBook(id: ID!): Boolean!
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public boolean deleteBook(@Argument UUID id) {
        bookService.deleteBook(id);
        return true;
    }
}
