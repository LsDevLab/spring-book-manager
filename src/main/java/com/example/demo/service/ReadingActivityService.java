package com.example.demo.service;

import com.example.demo.dto.response.ActiveReaderDTO;
import com.example.demo.model.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

// Manages live reading activity entirely in Redis — no DB queries for reads.
//
// Three Redis data structures work together:
//   Hash       (reading_session:<userId>)         — session details (one per active reader)
//   Sorted Set (active_readers:<topic>)           — who's reading by topic, ordered by last activity
//   Set        (book:<bookId>:active_readers)     — who's reading a specific book
//
// Why three? Each answers a different question efficiently:
//   Hash  → "What is user X reading right now?" (HGETALL — O(n) where n = fields, so O(1) in practice)
//   ZSet  → "Who's reading BACKEND books, most recent first?" (ZREVRANGE — O(log N + M))
//   Set   → "How many people are reading Clean Code?" (SCARD — O(1))
@Service
@RequiredArgsConstructor
public class ReadingActivityService {

    private final StringRedisTemplate redisTemplate;

    private static final String SESSION_PREFIX = "reading_session:";
    private static final String TOPIC_PREFIX = "active_readers:";
    private static final String BOOK_PREFIX = "book:";
    private static final String BOOK_SUFFIX = ":active_readers";

    // ── WRITE METHODS ────────────────────────────────────────

    // Called when a user starts reading (status → READING).
    // Writes to all 3 structures at once.
    //
    // HSET reading_session:<userId> bookId <bookId> bookTitle <title> topic <topic> ...
    //   → Hash stores the full session as field-value pairs. Like a mini DB row.
    //   → opsForHash().putAll() sets multiple fields in one call (maps to HMSET).
    //
    // ZADD active_readers:<topic> <timestamp> <userId>
    //   → Sorted Set with score = timestamp. Higher score = more recent activity.
    //   → opsForZSet().add() adds/updates the member's score.
    //
    // SADD book:<bookId>:active_readers <userId>
    //   → Set of unique userIds. Duplicates are ignored automatically.
    //   → opsForSet().add() maps to SADD.
    public void startReading(UUID userId, String username, UUID bookId, String bookTitle, Topic topic, int currentPage) {
        String sessionKey = SESSION_PREFIX + userId;
        String topicKey = TOPIC_PREFIX + topic.name();
        String bookKey = BOOK_PREFIX + bookId + BOOK_SUFFIX;

        // Hash — store session details
        Map<String, String> session = Map.of(
                "bookId", bookId.toString(),
                "bookTitle", bookTitle,
                "username", username,
                "topic", topic.name(),
                "currentPage", String.valueOf(currentPage),
                "startedAt", Instant.now().toString()
        );
        redisTemplate.opsForHash().putAll(sessionKey, session);

        // Sorted Set — add user to topic's active readers, scored by current timestamp
        redisTemplate.opsForZSet().add(topicKey, userId.toString(), Instant.now().toEpochMilli());

        // Set — add user to this book's active readers
        redisTemplate.opsForSet().add(bookKey, userId.toString());
    }

    // Called when a user updates their reading progress (currentPage changes).
    // Updates the Hash field and refreshes the Sorted Set score (moves user to "most recent").
    //
    // HSET reading_session:<userId> currentPage <newPage>
    //   → opsForHash().put() updates a single field in the hash.
    //
    // ZADD active_readers:<topic> <newTimestamp> <userId>
    //   → ZADD with an existing member updates its score (doesn't create a duplicate).
    public void updateProgress(UUID userId, int currentPage) {
        String sessionKey = SESSION_PREFIX + userId;

        // Read the topic from the existing session to update the right Sorted Set
        Object topic = redisTemplate.opsForHash().get(sessionKey, "topic");
        if (topic == null) return; // no active session — nothing to update

        // Update page in the session hash
        redisTemplate.opsForHash().put(sessionKey, "currentPage", String.valueOf(currentPage));

        // Refresh timestamp in the topic's sorted set (user bubbles to top as "most recently active")
        String topicKey = TOPIC_PREFIX + topic;
        redisTemplate.opsForZSet().add(topicKey, userId.toString(), Instant.now().toEpochMilli());
    }

    // Called when a user completes a book (status → COMPLETED) or removes it from their list.
    // Removes the user from all 3 structures.
    //
    // DEL  reading_session:<userId>       → delete the entire hash
    // ZREM active_readers:<topic> <userId> → remove from sorted set
    // SREM book:<bookId>:active_readers <userId> → remove from set
    public void stopReading(UUID userId) {
        String sessionKey = SESSION_PREFIX + userId;

        // Read session details before deleting — we need topic and bookId to clean up the other structures
        Map<Object, Object> session = redisTemplate.opsForHash().entries(sessionKey);
        if (session.isEmpty()) return; // no active session

        String topic = (String) session.get("topic");
        String bookId = (String) session.get("bookId");

        // Remove from all 3 structures
        redisTemplate.delete(sessionKey);
        redisTemplate.opsForZSet().remove(TOPIC_PREFIX + topic, userId.toString());
        redisTemplate.opsForSet().remove(BOOK_PREFIX + bookId + BOOK_SUFFIX, userId.toString());
    }

    // ── READ METHODS ───────────────────────────────────────────

    // "Who's reading BACKEND books right now, most recently active first?"
    //
    // Step 1: ZREVRANGE active_readers:<topic> 0 <limit>
    //   → Returns userIds ordered by score (timestamp) descending — most recent first.
    //   → "rev" = reverse order. ZRANGE would give oldest first.
    //
    // Step 2: HGETALL reading_session:<userId> for each
    //   → Fetches the full session hash for each active reader.
    public List<ActiveReaderDTO> activeReadersByTopic(Topic topic, int limit) {
        String topicKey = TOPIC_PREFIX + topic.name();

        // ZREVRANGE — get top N userIds by most recent activity
        Set<String> userIds = redisTemplate.opsForZSet().reverseRange(topicKey, 0, limit - 1);
        if (userIds == null || userIds.isEmpty()) return List.of();

        return userIds.stream()
                .map(this::getSession)
                .filter(Objects::nonNull)
                .toList();
    }

    // "Who's reading this specific book?"
    //
    // SMEMBERS book:<bookId>:active_readers
    //   → Returns all members of the set (all userIds reading this book).
    //   → Unlike Sorted Set, a Set has no ordering — just unique membership.
    public List<ActiveReaderDTO> activeReadersByBook(UUID bookId) {
        String bookKey = BOOK_PREFIX + bookId + BOOK_SUFFIX;

        Set<String> userIds = redisTemplate.opsForSet().members(bookKey);
        if (userIds == null || userIds.isEmpty()) return List.of();

        return userIds.stream()
                .map(this::getSession)
                .filter(Objects::nonNull)
                .toList();
    }

    // "How many people are reading this book right now?"
    //
    // SCARD book:<bookId>:active_readers
    //   → Returns the cardinality (size) of the set. O(1) — instant, regardless of set size.
    //   → Compare with COUNT(*) in SQL which scans rows.
    public long activeReadersByBookCount(UUID bookId) {
        String bookKey = BOOK_PREFIX + bookId + BOOK_SUFFIX;
        Long count = redisTemplate.opsForSet().size(bookKey);
        return count != null ? count : 0;
    }

    // ── HELPER ────────────────────────────────────────────────

    // Fetches a reading session hash and converts it to a DTO.
    // Returns null if the session doesn't exist (user may have completed between the two calls).
    private ActiveReaderDTO getSession(String userId) {
        Map<Object, Object> session = redisTemplate.opsForHash().entries(SESSION_PREFIX + userId);
        if (session.isEmpty()) return null;
        return ActiveReaderDTO.fromRedisHash(session);
    }

}
