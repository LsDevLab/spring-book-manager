package com.example.demo.service;

import com.example.demo.dto.request.LinkUserAuthMethodRequestDTO;
import com.example.demo.dto.request.LoginRequestDTO;
import com.example.demo.dto.request.RegisterRequestDTO;
import com.example.demo.dto.response.LoginResponseDTO;
import com.example.demo.exception.UserAlreadyExistsException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.exception.WrongCredentialsException;
import com.example.demo.model.AuthMethod;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.UserAuthMethod;
import com.example.demo.security.JwtTokenProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestClient keycloakRestClient;

    @Value("${app.keycloak.token-uri}")
    private String keycloakTokenUri;
    @Value("${app.keycloak.client-id}")
    private String keycloakClientId;
    @Value("${app.keycloak.admin-client-id}")
    private String keycloakAdminClientId;
    @Value("${app.keycloak.admin-client-secret}")
    private String keycloakAdminClientSecret;
    @Value("${app.keycloak.admin-uri}")
    private String keycloakAdminUri;

    /**
     * Registers a new user with the specified authentication method.
     *
     * @param dto registration data including username, email, password, and auth method
     * @return the created {@link UserAuthMethod} record
     * @throws UserAlreadyExistsException if a user with the same username or email already exists
     */
    @Transactional
    public UserAuthMethod registerUser(RegisterRequestDTO dto){
        if (dto.getAuthMethod() == AuthMethod.SELF) {
            return userService.createUserWithRegistrationMethod(
                    dto.getUsername(),
                    dto.getEmail(),
                    passwordEncoder.encode(dto.getPassword()),
                    Role.USER,
                    AuthMethod.SELF
            );
        } else {
            UserAuthMethod userAuthMethod = userService.createUserWithRegistrationMethod(
                    dto.getUsername(),
                    dto.getEmail(),
                    "",
                    Role.USER,
                    AuthMethod.KEYCLOAK
            );
            registerKeycloak(
                    dto.getUsername(),
                    dto.getEmail(),
                    dto.getPassword()
            );
            return userAuthMethod;
        }
    }

    /**
     * Links an additional authentication method to an existing user's account.
     * Requires the user to be authenticated (JWT), so only the account owner can link methods.
     *
     * @param dto      the new auth method and its credentials
     * @param userId   the authenticated user's ID (extracted from JWT)
     * @return the created {@link UserAuthMethod} record
     * @throws UserNotFoundException if the user does not exist
     * @throws AuthMethodAlreadyRegisteredException if the method is already linked
     */
    @Transactional
    public UserAuthMethod linkAuthMethodForUser(LinkUserAuthMethodRequestDTO dto, UUID userId){
        if (dto.getAuthMethod() == AuthMethod.SELF) {
            return userService.updateUserWithRegistrationMethod(
                userId,
                passwordEncoder.encode(dto.getPassword()),
                AuthMethod.SELF
            );
        } else {
            UserAuthMethod userAuthMethod = userService.updateUserWithRegistrationMethod(
                    userId,
                    null,
                    AuthMethod.KEYCLOAK
            );
            registerKeycloak(
                    userAuthMethod.getUser().getUsername(),
                    userAuthMethod.getUser().getEmail(),
                    dto.getPassword()
            );
            return userAuthMethod;
        }
    }

    /**
     * Creates a user in Keycloak via the Admin REST API.
     *
     * @throws UserAlreadyExistsException if the user already exists in Keycloak
     */
    protected void registerKeycloak(String username, String email, String password) {

        // Create user in Keycloak via Admin REST API
        String adminToken = getKeycloakAdminToken();
        try {
            keycloakRestClient.post()
                    .uri(keycloakAdminUri + "/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    // Keycloak 24+ has User Profile enabled by default, which makes
                    // firstName/lastName required. Missing them causes "Account is not
                    // fully set up" on the password grant even with no required actions.
                    .body(Map.of(
                            "username", username,
                            "email", email,
                            "firstName", username,
                            "lastName", username,
                            "enabled", true,
                            "emailVerified", true,
                            "requiredActions", List.of(),
                            "credentials", List.of(Map.of(
                                    "type", "password",
                                    "value", password,
                                    "temporary", false
                            ))
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new UserAlreadyExistsException();
        }


    }

    /**
     * Obtains an admin access token from Keycloak using client credentials grant.
     *
     * @return the admin bearer token
     */
    private String getKeycloakAdminToken() {
        var formData = new LinkedMultiValueMap<String, String>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", keycloakAdminClientId);
        formData.add("client_secret", keycloakAdminClientSecret);

        Map<String, String> response = keycloakRestClient.post()
                .uri(keycloakTokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return response.get("access_token");
    }

    /**
     * Authenticates a user and returns a JWT token.
     * Delegates to the appropriate login method based on the requested auth method.
     *
     * @param dto login credentials including username, email, password, and auth method
     * @return a {@link LoginResponseDTO} containing the JWT token
     * @throws WrongCredentialsException if authentication fails
     */
    public LoginResponseDTO login(LoginRequestDTO dto){
        if(dto.getAuthMethod().equals(AuthMethod.SELF)){
            return loginSelf(dto.getUsername(), dto.getEmail(), dto.getPassword());
        } else {
            return loginKeycloak(dto.getUsername(), dto.getEmail(), dto.getPassword());
        }
    }

    /**
     * Authenticates against locally stored credentials (bcrypt-hashed password).
     *
     * @throws WrongCredentialsException if the user doesn't exist, hasn't registered with SELF, or the password is wrong
     */
    public LoginResponseDTO loginSelf(String username, String email, String password){

        try {
            User user = userService.getUserByUsernameOrEmail(
                    username,
                    email
            );
            if(user.getAuthMethods().stream()
                    .noneMatch(ur -> ur.getAuthMethod().equals(AuthMethod.SELF))){
                throw new WrongCredentialsException();
            }
            if(passwordEncoder.matches(password, user.getPassword())){
                return new LoginResponseDTO(jwtTokenProvider.generateToken(user, user.getRole()));
            } else {
                throw new WrongCredentialsException();
            }
        } catch (UserNotFoundException ex) {
            throw new WrongCredentialsException();
        }
    }

    /**
     * Authenticates against Keycloak using the Resource Owner Password Credentials grant.
     *
     * @throws WrongCredentialsException if the user doesn't exist, hasn't registered with KEYCLOAK, or Keycloak rejects the credentials
     */
    public LoginResponseDTO loginKeycloak(String username, String email, String password){
        try {
            User user = userService.getUserByUsernameOrEmail(
                    username,
                    email
            );
            if(user.getAuthMethods().stream()
                    .noneMatch(ur -> ur.getAuthMethod().equals(AuthMethod.KEYCLOAK))){
                throw new WrongCredentialsException();
            }
            var formData = new LinkedMultiValueMap<String, String>();
            formData.add("grant_type", "password");
            formData.add("client_id", keycloakClientId);
            formData.add("username", username);
            formData.add("password", password);
            Map<String, String> response = keycloakRestClient
                    .post()
                    .uri(keycloakTokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return new LoginResponseDTO(response.get("access_token"));
        } catch (Exception e) {
            throw new WrongCredentialsException();
        }
    }

}
