package com.example.demo.security;

import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtDecoder jwtDecoder;
    private final UserService userService;

    @Value("${app.keycloak.issuer}")
    private String keycloakIssuer;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            UsernamePasswordAuthenticationToken auth = isKeycloakToken(token)
                    ? authenticateKeycloak(token)
                    : authenticateSelf(token);

            if (auth != null) {
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    // Keycloak path — RSA validation via Spring's JwtDecoder.
    // jwtDecoder fetches Keycloak's public keys from the JWKS endpoint automatically.
    private UsernamePasswordAuthenticationToken authenticateKeycloak(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);

            String username = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");

            // Keycloak nests roles under "realm_access.roles".
            // It includes its own defaults like "default-roles-book-manager-realm",
            // "offline_access", "uma_authorization" — we only want our app's roles.
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            List<String> roles = (List<String>) realmAccess.get("roles");

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .filter(r -> r.equals("USER") || r.equals("ADMIN"))
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();

            User user = userService.getUserByUsername(username);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            auth.setDetails(new AuthUserDetails(user.getId(), email));
            return auth;
        } catch (Exception e) {
            return null;
        }
    }

    // Self-issued path — HMAC validation via our JwtTokenProvider.
    private UsernamePasswordAuthenticationToken authenticateSelf(String token) {
        if (!jwtTokenProvider.isValid(token)) return null;

        Claims claims = jwtTokenProvider.extractAllClaims(token);
        String username = claims.getSubject();
        String userId = claims.get("userId", String.class);
        String email = claims.get("email", String.class);
        Role role = Role.valueOf(claims.get("role", String.class));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                );
        auth.setDetails(new AuthUserDetails(UUID.fromString(userId), email));
        return auth;
    }

    // Peek at the token's issuer claim without full validation.
    // Decodes the payload (base64) and checks if "iss" matches Keycloak.
    private boolean isKeycloakToken(String token) {
        try {
            String payload = token.split("\\.")[1];
            String decoded = new String(Base64.getUrlDecoder().decode(payload));
            return decoded.contains("\"iss\":\"" + keycloakIssuer + "\"");
        } catch (Exception e) {
            return false;
        }
    }
}