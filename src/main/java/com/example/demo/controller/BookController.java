package com.example.demo.controller;

import com.example.demo.dto.BookMapper;
import com.example.demo.dto.request.BookRequestDTO;
import com.example.demo.dto.response.BookResponseDTO;
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

    /**
     * GET /api/books/search — paginated and sortable book list.
     *
     * <p>Spring automatically binds query params to the Pageable parameter:</p>
     * <ul>
     *   <li>{@code ?page=0} — page number (0-based)</li>
     *   <li>{@code ?size=10} — items per page</li>
     *   <li>{@code ?sort=title,asc} — sort by field and direction</li>
     * </ul>
     *
     * <p>The returned Page object includes metadata: totalElements, totalPages,
     * number (current page), size, first, last — all serialized into the JSON response.</p>
     *
     * @param pageable injected by Spring from query params
     * @return 200 OK with paginated response
     */
    @GetMapping("/search")
    @Operation(summary = "Search books with pagination", description = "Paginated and sortable book list with optional topic filter")
    @ApiResponse(responseCode = "200", description = "Paginated book results")
    public ResponseEntity<Page<BookResponseDTO>> searchBooks(
            @Parameter(description = "Filter by topic", required = false)
            @RequestParam(required = false) Topic topic,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                bookService.searchBooks(topic, pageable)
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

    @GetMapping("/recommendations")
    @Operation(summary = "Get most read books by topic", description = "Returns books ranked by number of readers for a given topic")
    @ApiResponse(responseCode = "200", description = "Paginated recommendations returned")
    public ResponseEntity<Page<BookResponseDTO>> getMostReadBooksByTopic(
            @Parameter(description = "Topic to get recommendations for", required = true)
            @RequestParam Topic topic,
            Pageable pageable){
        return ResponseEntity.ok(
                userBookService.getMostReadBookByTopic(topic, pageable)
                    .map(BookResponseDTO::fromEntity)
        );
    }


}
