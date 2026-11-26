package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.LoginRequest;
import com.precisionnutrition.model.dto.LoginResponse;
import com.precisionnutrition.model.dto.UserResponse;
import com.precisionnutrition.model.entity.User;
import com.precisionnutrition.repository.UserRepository;
import com.precisionnutrition.security.AuthenticatedUser;
import com.precisionnutrition.security.JwtService;
import com.precisionnutrition.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private static final String INVALID_CREDENTIALS_MSG = "E-mail ou senha inválidos";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MSG));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
        }

        String token = jwtService.generateToken(user);
        log.info("Login autenticado com sucesso para usuário {} (role={})", user.getId(), user.getRole());

        return ResponseEntity.ok(new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserResponse.from(user)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        AuthenticatedUser current = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MSG));
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
