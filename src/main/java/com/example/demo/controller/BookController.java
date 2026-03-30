package com.example.demo.controller;

import com.example.demo.dto.BookMapper;
import com.example.demo.dto.request.BookRequestDTO;
import com.example.demo.dto.response.BookResponseDTO;
import com.example.demo.model.Topic;
import com.example.demo.service.BookService;
import com.example.demo.service.UserBookService;
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
public class BookController {

    private final BookService bookService;
    private final UserBookService userBookService;
    private final BookMapper bookMapper;

    // ── GET ALL ───────────────────────────────────────────────
    // Approach: manual DTO conversion with stream + method reference
    @GetMapping
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
    public ResponseEntity<Page<BookResponseDTO>> searchBooks(
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
    public ResponseEntity<BookResponseDTO> createBook(@RequestBody @Valid BookRequestDTO dto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookResponseDTO.fromEntity(bookService.createBook(dto.toEntity())));
    }

    // ── UPDATE ────────────────────────────────────────────────
    // Approach: MapStruct mapper (bookMapper.toEntity() / toResponseDTO())
    // Error handling: try/catch in controller (vs @ControllerAdvice used in DELETE)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
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
    public ResponseEntity<Page<BookResponseDTO>> getMostReadBooksByTopic(@RequestParam Topic topic, Pageable pageable){
        return ResponseEntity.ok(
                userBookService.getMostReadBookByTopic(topic, pageable)
                    .map(BookResponseDTO::fromEntity)
        );
    }


}
