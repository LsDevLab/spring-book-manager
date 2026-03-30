package com.example.demo.service;

import com.example.demo.dto.request.LoginRequestDTO;
import com.example.demo.dto.request.RegisterRequestDTO;
import com.example.demo.dto.response.LoginResponseDTO;
import com.example.demo.exception.UserAlreadyExistsException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.exception.WrongCredentialsException;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    public User registerUser(RegisterRequestDTO dto){
        if(userService.userExistsByUsernameOrEmail(
                dto.getUsername(),
                dto.getEmail()
        )) {
            throw new UserAlreadyExistsException();
        }
        User userToCreate = dto.toEntity();
        userToCreate.setPassword(passwordEncoder.encode(userToCreate.getPassword()));
        userToCreate.setRole(Role.USER);
        return userService.createUser(userToCreate);
    }

    public LoginResponseDTO login(LoginRequestDTO dto){
        try {
            User user = userService.getUserByUsernameOrEmail(
                    dto.getUsername(),
                    dto.getEmail()
            );
            if(passwordEncoder.matches(dto.getPassword(), user.getPassword())){
                return new LoginResponseDTO(jwtTokenProvider.generateToken(user, user.getRole()));
            } else {
                throw new WrongCredentialsException();
            }
        } catch (UserNotFoundException ex) {
            throw new WrongCredentialsException();
        }
    }

}
