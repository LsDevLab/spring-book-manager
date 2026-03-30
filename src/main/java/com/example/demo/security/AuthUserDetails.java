package com.example.demo.security;

import java.util.UUID;

public record AuthUserDetails(UUID userId, String email) {}