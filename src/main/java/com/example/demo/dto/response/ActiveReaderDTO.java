package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

// Represents a user's live reading session — built from a Redis Hash (reading_session:<userId>).
// Public-facing DTO: no userId or bookId exposed, username is obfuscated for privacy.
@Data
@AllArgsConstructor
@Schema(description = "Active reading session details (public)")
public class ActiveReaderDTO {

    @Schema(description = "Obfuscated username", example = "jo******")
    private String username;

    @Schema(description = "Book title", example = "Clean Code")
    private String bookTitle;

    @Schema(description = "Current page the reader is on", example = "142")
    private int currentPage;

    @Schema(description = "When the user started reading", example = "2026-04-02T10:30:00Z")
    private String startedAt;

    // Factory method — converts a Redis Hash (Map<Object, Object>) into a typed DTO.
    // Redis stores everything as strings, so we parse UUIDs and ints here.
    // Username is obfuscated before exposing to the public.
    public static ActiveReaderDTO fromRedisHash(Map<Object, Object> hash) {
        return new ActiveReaderDTO(
                obfuscateUsername((String) hash.get("username")),
                (String) hash.get("bookTitle"),
                Integer.parseInt((String) hash.get("currentPage")),
                (String) hash.get("startedAt")
        );
    }

    // "john_doe" → "jo******"
    // Shows first 2 characters, replaces the rest with asterisks.
    private static String obfuscateUsername(String username) {
        if (username == null || username.length() <= 2) return "**";
        return username.substring(0, 2) + "*".repeat(username.length() - 2);
    }
}