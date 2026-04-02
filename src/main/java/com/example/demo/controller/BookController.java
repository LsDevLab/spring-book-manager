package com.example.demo.controller;

import com.example.demo.dto.BookMapper;
import com.example.demo.dto.request.BookRequestDTO;
import com.example.demo.dto.request.BookSearchDTO;
import com.example.demo.dto.request.SimplePageable;
import com.example.demo.dto.response.BookResponseDTO;
import com.example.demo.dto.response.SimplePage;
import com.example.demo.model.Book;
import com.example.demo.model.Topic;
import com.example.demo.service.BookService;
import com.example.demo.service.UserBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "Books", description = "Book catalog management")
@SecurityRequirement(name = "bearerAuth")
public class BookController {

    private final BookService bookService;
    private final UserBookService userBookService;
    private final BookMapper bookMapper;

    // ── GET ALL ───────────────────────────────────────────────
    // Approach: manual DTO conversion with stream + method reference
    @GetMapping
    @Operation(summary = "List all books")
    @ApiResponse(responseCode = "200", description = "List of all books returned")
    public ResponseEntity<List<BookResponseDTO>> getAllBooks(){
        return ResponseEntity.ok(
                bookService.getAllBooks().stream()
                        .map(BookResponseDTO::fromEntity)
                        .toList()
        );
    }

    // ── SEARCH ────────────────────────────────────────────────
    // Query params are auto-bound to BookSearchDTO fields by Spring MVC (model attribute binding).
    // @ModelAttribute is optional — Spring does this by default for non-annotated complex types.
    // Only non-null fields become active filters (via JPA Specifications in the service layer).
    // Example: GET /api/books/search?title=clean&topic=BACKEND&minPages=100
    @GetMapping("/search")
    @Operation(summary = "Search books with dynamic filters",
            description = "Paginated, sortable book search. Filter by any combination of: title, author, topic, minPages, maxPages. All filters are optional — omitted filters are ignored.")
    @ApiResponse(responseCode = "200", description = "Paginated book results matching the filters")
    public ResponseEntity<Page<BookResponseDTO>> searchBooks(
            @ModelAttribute BookSearchDTO bookSearchDTO,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                bookService.searchBooks(bookSearchDTO, pageable)
                        .map(BookResponseDTO::fromEntity)
        );
    }

    // ── GET BY ID ─────────────────────────────────────────────
    // Approach: manual DTO conversion, Optional chaining
    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID")
    @ApiResponse(responseCode = "200", description = "Book found")
    @ApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<BookResponseDTO> getBookById(@PathVariable UUID id) {
        return bookService.getBookById(id)
                .map(BookResponseDTO::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── CREATE ────────────────────────────────────────────────
    // Approach: manual DTO conversion (dto.toEntity() / fromEntity())
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new book (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Book created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<BookResponseDTO> createBook(@RequestBody @Valid BookRequestDTO dto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookResponseDTO.fromEntity(bookService.createBook(dto.toEntity())));
    }

    // ── UPDATE ────────────────────────────────────────────────
    // Approach: MapStruct mapper (bookMapper.toEntity() / toResponseDTO())
    // Error handling: try/catch in controller (vs @ControllerAdvice used in DELETE)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a book (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Book updated")
    @ApiResponse(responseCode = "404", description = "Book not found")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<BookResponseDTO> updateBook(@PathVariable UUID id, @RequestBody @Valid BookRequestDTO dto){
        return ResponseEntity.ok(
                bookMapper.toResponseDTO(bookService.updateBook(id, bookMapper.toEntity(dto)))
        );
    }

    // ── DELETE ─────────────────────────────────────────────────
    // Error handling: no try/catch — BookNotFoundException caught by
    // @ControllerAdvice (GlobalExceptionHandler) which returns 404 automatically
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a book (ADMIN)")
    @ApiResponse(responseCode = "204", description = "Book deleted")
    @ApiResponse(responseCode = "404", description = "Book not found")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    // ── NOTES ──────────────────────────────────────────────────
    //
    // ResponseEntity building — two styles explored:
    //   Constructor:  new ResponseEntity<>(body, HttpStatus.OK)
    //   Builder:      ResponseEntity.ok(body)
    //                 ResponseEntity.status(HttpStatus.CREATED).body(body)
    //                 ResponseEntity.noContent().build()
    //
    // DTO conversion — two approaches explored:
    //   Manual:       dto.toEntity() / BookResponseDTO.fromEntity(book)
    //   MapStruct:    bookMapper.toEntity(dto) / bookMapper.toResponseDTO(book)
    //
    // Error handling — two approaches explored:
    //   try/catch:    in the controller method (see UPDATE)
    //   @ControllerAdvice: GlobalExceptionHandler catches BookNotFoundException (see DELETE)

    // Recommendations — returns books ranked by reader count.
    // Sorting is fixed (ORDER BY COUNT in JPQL), so we only expose page/size to the client.
    //
    // Old approach (exposed sort + full Pageable noise in response):
    //   public ResponseEntity<Page<BookResponseDTO>> getMostReadBooksByTopic(
    //           @RequestParam Topic topic, Pageable pageable) { ... }
    //
    // New approach: SimplePageable (page/size only, no sort), SimplePage (clean response).
    @GetMapping("/recommendations")
    @Operation(summary = "Get most read books by topic", description = "Returns books ranked by number of readers for a given topic")
    @ApiResponse(responseCode = "200", description = "Paginated recommendations returned")
    public ResponseEntity<SimplePage<BookResponseDTO>> getMostReadBooksByTopic(
            @Parameter(description = "Topic to get recommendations for", required = true)
            @RequestParam Topic topic,
            @ModelAttribute SimplePageable simplePageable){
        // Service returns SimplePage<Book> — map content to DTOs for the response.
        SimplePage<Book> books = userBookService.getMostReadBookByTopic(topic, simplePageable);
        return ResponseEntity.ok(books.map(BookResponseDTO::fromEntity));
    }


}
