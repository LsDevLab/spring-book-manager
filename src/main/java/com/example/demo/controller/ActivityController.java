package com.example.demo.controller;

import com.example.demo.dto.response.ActiveReaderDTO;
import com.example.demo.dto.response.HllComparisonDTO;
import com.example.demo.model.Topic;
import com.example.demo.service.ReadingActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Public endpoints — no authentication required (configured in SecurityConfiguration).
// All data comes from Redis, zero database queries.
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "Live reading activity feed (public)")
public class ActivityController {

    private final ReadingActivityService readingActivityService;

    // "Who's reading BACKEND books right now?"
    // Sorted by most recently active (Sorted Set score = timestamp).
    @GetMapping("/topic/{topic}")
    @Operation(summary = "Active readers by topic", description = "Returns users currently reading books in a given topic, ordered by most recently active")
    @ApiResponse(responseCode = "200", description = "List of active readers")
    public ResponseEntity<List<ActiveReaderDTO>> activeReadersByTopic(
            @Parameter(description = "Book topic", required = true)
            @PathVariable Topic topic,
            @Parameter(description = "Max number of results")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(readingActivityService.activeReadersByTopic(topic, limit));
    }

    // "Who's reading Clean Code right now?"
    // Unordered (comes from a Set, not a Sorted Set).
    @GetMapping("/book/{bookId}")
    @Operation(summary = "Active readers of a book", description = "Returns users currently reading a specific book")
    @ApiResponse(responseCode = "200", description = "List of active readers")
    public ResponseEntity<List<ActiveReaderDTO>> activeReadersByBook(
            @Parameter(description = "Book ID", required = true)
            @PathVariable UUID bookId) {
        return ResponseEntity.ok(readingActivityService.activeReadersByBook(bookId));
    }

    // "How many people are reading Clean Code?"
    // Single O(1) Redis call (SCARD).
    @GetMapping("/book/{bookId}/count")
    @Operation(summary = "Active reader count for a book", description = "Returns the number of users currently reading a specific book")
    @ApiResponse(responseCode = "200", description = "Reader count")
    public ResponseEntity<Long> activeReadersCount(
            @Parameter(description = "Book ID", required = true)
            @PathVariable UUID bookId) {
        return ResponseEntity.ok(readingActivityService.activeReadersByBookCount(bookId));
    }

    // "How many unique users have ever read books in this topic?"
    // Uses HyperLogLog (PFCOUNT) — approximate count, constant ~12KB memory.
    @GetMapping("/topic/{topic}/unique-readers")
    @Operation(summary = "Unique readers by topic (HLL)", description = "Approximate count of unique users who have ever read a book in this topic, using HyperLogLog (PFCOUNT)")
    @ApiResponse(responseCode = "200", description = "Approximate unique reader count")
    public ResponseEntity<Long> uniqueReadersByTopic(
            @Parameter(description = "Topic", required = true)
            @PathVariable Topic topic) {
        return ResponseEntity.ok(readingActivityService.uniqueReadersByTopic(topic));
    }

    // "How many unique users have ever read this book?"
    // Uses HyperLogLog (PFCOUNT) — approximate count, constant ~12KB memory.
    @GetMapping("/book/{bookId}/unique-readers")
    @Operation(summary = "Unique readers of a book (HLL)", description = "Approximate count of unique users who have ever read this book, using HyperLogLog (PFCOUNT)")
    @ApiResponse(responseCode = "200", description = "Approximate unique reader count")
    public ResponseEntity<Long> uniqueReadersByBook(
            @Parameter(description = "Book ID", required = true)
            @PathVariable UUID bookId) {
        return ResponseEntity.ok(readingActivityService.uniqueReadersByBook(bookId));
    }

    // Simulation endpoint — adds N random userIds to both a HyperLogLog and a Set,
    // then compares count accuracy and memory usage side by side.
    // Try: /api/activity/simulate?count=50000 — HLL will use ~200x less memory.
    @GetMapping("/simulate")
    @Operation(
            summary = "Simulate HLL vs Set comparison",
            description = "Adds N random users to both a HyperLogLog and a Set, then compares count accuracy " +
                    "(PFCOUNT vs SCARD) and memory usage (MEMORY USAGE). Temporary keys are cleaned up after."
    )
    @ApiResponse(responseCode = "200", description = "Comparison results showing count and memory differences")
    public ResponseEntity<HllComparisonDTO> simulateComparison(
            @Parameter(description = "Number of unique users to simulate", example = "50000")
            @RequestParam(defaultValue = "50000") int count) {
        return ResponseEntity.ok(readingActivityService.simulateHllVsSet(count));
    }
}