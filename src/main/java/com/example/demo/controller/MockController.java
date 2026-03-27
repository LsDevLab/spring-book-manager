package com.example.demo.controller;

import com.example.demo.dto.request.BookRequestDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

// Mock endpoint that serves local JSON data — simulates an external trending books API.
// Used by the nightly scheduled job (Phase 7.2) via RestClient.
// In production this would be replaced by calling a real external API (e.g. Open Library).
@RestController
@RequestMapping("/api/mock")
public class MockController {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @GetMapping("/trending-books")
    public ResponseEntity<List<BookRequestDTO>> getTrendingBooks() throws IOException {
        InputStream stream = new ClassPathResource("mock/trending-books.json").getInputStream();
        List<BookRequestDTO> books = jsonMapper.readValue(
                stream,
                jsonMapper.getTypeFactory().constructCollectionType(List.class, BookRequestDTO.class));
        return ResponseEntity.ok(books);
    }

}