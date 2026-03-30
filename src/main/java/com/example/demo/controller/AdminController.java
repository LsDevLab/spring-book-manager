package com.example.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

// Admin endpoints for cache management and other operational tasks.
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    // Injects all CacheManager beans (caffeineCacheManager + redisCacheManager)
    private final Collection<CacheManager> cacheManagers;

    // POST /api/admin/cache/evict — evicts all entries from all caches (Caffeine + Redis).
    @PostMapping("/cache/evict")
    @Operation(summary = "Evict all cache entries", description = "Clears all entries from all cache managers (Caffeine + Redis)")
    @ApiResponse(responseCode = "200", description = "All caches cleared")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<String> evictAllCaches() {
        cacheManagers.forEach(cacheManager ->
                cacheManager.getCacheNames().forEach(name -> {
                    Cache cache = cacheManager.getCache(name);
                    if (cache != null) {
                        cache.clear();
                    }
                }));
        return ResponseEntity.ok("All caches cleared");
    }

}
