package com.precisionnutrition.config;

import com.precisionnutrition.model.entity.User;
import com.precisionnutrition.model.enums.UserRole;
import com.precisionnutrition.repository.UserRepository;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provisionamento seguro e idempotente do usuário ADMIN via variáveis de ambiente.
 * Executa em dev e prod sem senhas hardcoded em código.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin.email:}")
    private String adminEmail;

    @Value("${app.security.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD não definidos no ambiente. Pulando provisionamento automático do administrador.");
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.info("Usuário administrador já existente para {}. Seed ignorado (idempotente).", normalizedEmail);
            return;
        }

        UUID assignedId = userRepository.existsById(AppConstants.DEFAULT_USER_ID)
                ? UUID.randomUUID()
                : AppConstants.DEFAULT_USER_ID;

        User admin = User.builder()
                .id(assignedId)
                .email(normalizedEmail)
                .name("Administrador")
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .active(true)
                .build();

        userRepository.save(admin);
        log.info("Usuário administrador provisionado com sucesso: id={}, email={}", admin.getId(), normalizedEmail);
    }
}
