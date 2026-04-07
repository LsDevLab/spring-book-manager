package com.example.demo.service;

import com.example.demo.dto.response.ActiveReaderDTO;
import com.example.demo.model.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.dto.response.HllComparisonDTO;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

// Manages live reading activity entirely in Redis — no DB queries for reads.
//
// Four Redis data structures work together:
//   Hash         (reading_session:<userId>)         — session details (one per active reader)
//   Sorted Set   (active_readers:<topic>)           — who's reading by topic, ordered by last activity
//   Set          (book:<bookId>:active_readers)     — who's reading a specific book
//   HyperLogLog  (unique_readers:topic/book:<id>)   — approximate unique reader count (cumulative, never removed)
//
// Each answers a different question efficiently:
//   Hash  → "What is user X reading right now?" (HGETALL — O(n) where n = fields, so O(1) in practice)
//   ZSet  → "Who's reading BACKEND books, most recent first?" (ZREVRANGE — O(log N + M))
//   Set   → "How many people are reading Clean Code?" (SCARD — O(1))
//   HLL   → "How many unique users have EVER read BACKEND books?" (PFCOUNT — O(1), ~12KB fixed memory)
@Service
@RequiredArgsConstructor
public class ReadingActivityService {

    private final StringRedisTemplate redisTemplate;

    private static final String SESSION_PREFIX = "reading_session:";
    private static final String TOPIC_PREFIX = "active_readers:";
    private static final String BOOK_PREFIX = "book:";
    private static final String BOOK_SUFFIX = ":active_readers";
    private static final String UNIQUE_TOPIC_PREFIX = "unique_readers:topic:";
    private static final String UNIQUE_BOOK_PREFIX = "unique_readers:book:";

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
        String uniqueTopicKey = UNIQUE_TOPIC_PREFIX + topic;
        String uniqueBookKey = UNIQUE_BOOK_PREFIX + bookId;

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

        // HyperLogLog — track cumulative unique readers (never removed, unlike Set/ZSet above).
        // PFADD unique_readers:topic:<topic> <userId>
        //   → Probabilistic structure: constant ~12KB memory regardless of cardinality.
        //   → Duplicate adds are ignored (like Set), but can't enumerate or remove members.
        //   → opsForHyperLogLog().add() maps to PFADD.
        redisTemplate.opsForHyperLogLog().add(uniqueTopicKey, userId.toString());
        redisTemplate.opsForHyperLogLog().add(uniqueBookKey, userId.toString());
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

    // "How many unique users have EVER read a BACKEND book?"
    //
    // PFCOUNT unique_readers:topic:<topic>
    //   → Returns the approximate cardinality of the HyperLogLog. O(1), constant memory.
    //   → Approximate: standard error of 0.81% — e.g. 50,000 real users → reports ~49,600–50,400.
    //   → opsForHyperLogLog().size() maps to PFCOUNT (Spring uses "size" to match Collection convention).
    //
    // Compare with SCARD (activeReadersByBookCount above):
    //   SCARD = exact count of who's reading NOW (Set members can be removed).
    //   PFCOUNT = approximate count of who has EVER read (HLL members can't be removed).
    public long uniqueReadersByTopic(Topic topic) {
        String uniqueTopicKey = UNIQUE_TOPIC_PREFIX + topic;
        Long count = redisTemplate.opsForHyperLogLog().size(uniqueTopicKey);
        return count != null ? count : 0;
    }

    // "How many unique users have EVER read Clean Code?"
    // Same as above but keyed by bookId instead of topic.
    public long uniqueReadersByBook(UUID bookId) {
        String uniqueBookKey = UNIQUE_BOOK_PREFIX + bookId;
        Long count = redisTemplate.opsForHyperLogLog().size(uniqueBookKey);
        return count != null ? count : 0;
    }

    // ── SIMULATION ───────────────────────────────────────────

    // Adds the same random userIds to both a HyperLogLog and a Set, then compares:
    //   - Count accuracy: PFCOUNT (approximate) vs SCARD (exact)
    //   - Memory usage: MEMORY USAGE on each key (HLL ~12KB fixed vs Set grows linearly)
    //
    // Uses temporary keys (sim:hll / sim:set) that are deleted after the comparison.
    // This is the "aha moment" — you'll see HLL use ~200x less memory for roughly the same answer.
    public HllComparisonDTO simulateHllVsSet(int userCount) {
        String hllKey = "sim:hll";
        String setKey = "sim:set";

        // Clean up any leftover keys from a previous run
        redisTemplate.delete(List.of(hllKey, setKey));

        // Add the same random userIds to both structures.
        // We batch in groups of 1000 using pipelines for performance — without pipelining,
        // each add() is a separate round-trip to Redis, making 50k users painfully slow.
        //
        // executePipelined() sends all commands in one batch, Redis processes them all,
        // then returns all results at once. Same commands, ~100x faster.
        List<String> userIds = new ArrayList<>(userCount);
        for (int i = 0; i < userCount; i++) {
            userIds.add(UUID.randomUUID().toString());
        }

        // Pipeline all PFADD + SADD commands in one round-trip
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] hllKeyBytes = hllKey.getBytes(StandardCharsets.UTF_8);
            byte[] setKeyBytes = setKey.getBytes(StandardCharsets.UTF_8);
            for (String userId : userIds) {
                byte[] userIdBytes = userId.getBytes(StandardCharsets.UTF_8);
                connection.hyperLogLogCommands().pfAdd(hllKeyBytes, userIdBytes);
                connection.setCommands().sAdd(setKeyBytes, userIdBytes);
            }
            return null;
        });

        // Read counts — PFCOUNT vs SCARD
        Long hllCount = redisTemplate.opsForHyperLogLog().size(hllKey);
        Long setCount = redisTemplate.opsForSet().size(setKey);

        // MEMORY USAGE <key> — returns bytes used by a key in Redis.
        // Spring's connection.execute() uses ByteArrayOutput which can't parse the integer response,
        // and the connection object is proxied so we can't cast to LettuceConnection.
        // Solution: use a Lua script. redis.call("MEMORY", "USAGE", key) returns the integer
        // directly, and Spring's script executor handles Long return types natively.
        DefaultRedisScript<Long> memoryScript = new DefaultRedisScript<>();
        memoryScript.setScriptText("return redis.call('MEMORY', 'USAGE', KEYS[1])");
        memoryScript.setResultType(Long.class);

        Long hllBytes = redisTemplate.execute(memoryScript, List.of(hllKey));
        Long setBytes = redisTemplate.execute(memoryScript, List.of(setKey));

        // Clean up temporary keys
        redisTemplate.delete(List.of(hllKey, setKey));

        long hll = hllCount != null ? hllCount : 0;
        long set = setCount != null ? setCount : 0;
        long hllMem = hllBytes != null ? hllBytes : 0;
        long setMem = setBytes != null ? setBytes : 0;
        long diff = Math.abs(set - hll);
        double errorPct = set > 0 ? (diff * 100.0) / set : 0;
        double savingsFactor = hllMem > 0 ? (double) setMem / hllMem : 0;

        return new HllComparisonDTO(userCount, hll, set, hllMem, setMem,
                Math.round(savingsFactor * 10.0) / 10.0, diff, Math.round(errorPct * 100.0) / 100.0);
    }

}
