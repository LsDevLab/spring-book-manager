package com.example.demo.security;

import com.example.demo.model.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        // 1. Get the Authorization header
        String authHeader = request.getHeader("Authorization");
        // 2. Check if it starts with "Bearer "
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 3. Extract the token (everything after "Bearer ")
            String token = authHeader.substring(7);
            // 4. Validate
            if (jwtTokenProvider.isValid(token)) {
                // 5. Extract all claims in a single parse and set SecurityContext
                Claims claims = jwtTokenProvider.extractAllClaims(token);
                String username = claims.getSubject();
                Role role = Role.valueOf(claims.get("role", String.class));
                String userId = claims.get("userId", String.class);
                String email = claims.get("email", String.class);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())) //  Spring Security's hasRole('ADMIN') automatically prepends ROLE_ before checking.
                    );
                auth.setDetails(new AuthUserDetails(UUID.fromString(userId), email));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        // 6. Always continue the filter chain
        filterChain.doFilter(request, response);
    }

}

