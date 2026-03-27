package com.example.demo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

// Admin endpoints for cache management and other operational tasks.
// In Phase 10 these will be restricted to ADMIN role via @PreAuthorize.
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    // Injects all CacheManager beans (caffeineCacheManager + redisCacheManager)
    private final Collection<CacheManager> cacheManagers;

    // POST /api/admin/cache/evict — evicts all entries from all caches (Caffeine + Redis).
    @PostMapping("/cache/evict")
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