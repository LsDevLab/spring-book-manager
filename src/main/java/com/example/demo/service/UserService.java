package com.example.demo.service;

import com.example.demo.exception.AuthMethodAlreadyRegisteredException;
import com.example.demo.exception.UserAlreadyExistsException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.AuthMethod;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.UserAuthMethod;
import com.example.demo.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link User} CRUD operations.
 *
 * <p>Unlike {@link BookService} which returns {@code Optional<Book>} from getById,
 * this service throws {@link UserNotFoundException} directly — keeping the controller simpler.
 * Both patterns are valid; this project keeps both for comparison.</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** Returns all users in the database. */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Returns a paginated list of users.
     *
     * @param pageable pagination parameters
     * @return a Page of users
     */
    public Page<User> searchUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(UUID id) {
        // Notice: unlike BookService which returns Optional<Book>,
        // here we throw immediately if not found.
        // This is a design choice — it keeps the controller simpler
        // because it never has to deal with Optional unwrapping.
        // Compare with BookController.getBookById() to see both approaches.
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Finds a user by username.
     *
     * @throws UserNotFoundException if no user exists with the given username
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }


    /**
     * Finds a user by username or email (whichever matches first).
     *
     * @throws UserNotFoundException if no user matches either field
     */
    public User getUserByUsernameOrEmail(String username, String email) {
        return userRepository.findByUsernameOrEmail(username, email)
                .orElseThrow(() -> new UserNotFoundException(username, email));
    }

    /** Checks whether a user with the given username or email already exists. */
    public boolean userExistsByUsernameOrEmail(String username, String email) {
        return userRepository.existsByUsernameOrEmail(username, email);
    }

    /**
     * Creates a new user. Throws if a user with the same username or email already exists.
     *
     * @throws UserAlreadyExistsException if username or email is taken
     */
    public User createUser(User user) {
        userRepository.findByUsernameOrEmail(user.getUsername(), user.getEmail())
            .ifPresent(u -> {
                throw new UserAlreadyExistsException();
            });
        return userRepository.save(user);
    }

    @Transactional
    public UserAuthMethod createUserWithRegistrationMethod(String username, String email, String hashedPassword, Role role, AuthMethod authMethod) {
        userRepository.findByUsernameOrEmail(username, email)
            .ifPresent(u -> {
                throw new UserAlreadyExistsException();
            });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setRole(role);

        UserAuthMethod reg = new UserAuthMethod();
        reg.setUser(user);
        reg.setAuthMethod(authMethod);
        reg.setRegistrationDate(LocalDateTime.now());
        user.getAuthMethods().add(reg);

        userRepository.save(user);

        return reg;
    }

    @Transactional
    public UserAuthMethod updateUserWithRegistrationMethod(UUID userId, String hashedPassword, AuthMethod authMethod) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        if(user.getAuthMethods().stream().anyMatch(rm -> rm.getAuthMethod().equals(authMethod))){
            throw new AuthMethodAlreadyRegisteredException(authMethod);
        }

        if(hashedPassword != null){
            user.setPassword(hashedPassword);
        }

        UserAuthMethod reg = new UserAuthMethod();
        reg.setUser(user);
        reg.setAuthMethod(authMethod);
        reg.setRegistrationDate(LocalDateTime.now());
        user.getAuthMethods().add(reg);

        userRepository.save(user);

        return reg;
    }

    /**
     * Updates username and email of an existing user.
     *
     * @param id          the user's UUID
     * @param updatedUser entity containing the new field values
     * @return the updated user
     * @throws UserNotFoundException if no user exists with the given ID
     */
    public User updateUser(UUID id, User updatedUser) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        existing.setUsername(updatedUser.getUsername());
        existing.setEmail(updatedUser.getEmail());
        return userRepository.save(existing);
    }

    /**
     * Deletes a user by ID.
     *
     * @throws UserNotFoundException if no user exists with the given ID
     */
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        userRepository.delete(user);
    }

}
