package com.example.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User roles for authorization")
public enum Role {
    USER, ADMIN
}
