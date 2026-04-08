package com.example.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

// Enum used by Book.topic — stored as STRING in DB via @Enumerated(EnumType.STRING)
@Schema(description = "Book topic categories")
public enum Topic {
    BACKEND, FRONTEND, DEVOPS, ALGORITHMS, ARCHITECTURE
}
