package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.AdminCreateUserRequest;
import com.precisionnutrition.model.dto.AdminUpdateUserStatusRequest;
import com.precisionnutrition.model.dto.UserResponse;
import com.precisionnutrition.model.entity.User;
import com.precisionnutrition.model.enums.UserRole;
import com.precisionnutrition.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    @Transactional
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("Já existe um usuário cadastrado com este e-mail.");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .name(request.name() != null && !request.name().isBlank() ? request.name().trim() : null)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role() != null ? request.role() : UserRole.USER)
                .active(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Novo usuário provisionado pelo administrador: id={}, role={}", saved.getId(), saved.getRole());

        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(saved));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserResponse>> listUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateUserStatusRequest request
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado para ID: " + id));
        user.setActive(request.active());
        User saved = userRepository.save(user);
        log.info("Status de acesso do usuário {} atualizado para active={}", saved.getId(), saved.isActive());
        return ResponseEntity.ok(UserResponse.from(saved));
    }
}
