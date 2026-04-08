package com.example.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Supported authentication methods")
public enum AuthMethod {
    KEYCLOAK, SELF
}
