package com.precisionnutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.AdminCreateUserRequest;
import com.precisionnutrition.model.dto.AdminUpdateUserStatusRequest;
import com.precisionnutrition.model.dto.LoginRequest;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.model.entity.User;
import com.precisionnutrition.model.enums.UserRole;
import com.precisionnutrition.repository.MealRepository;
import com.precisionnutrition.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdMealIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID mealId : createdMealIds) {
            mealRepository.deleteById(mealId);
        }
        createdMealIds.clear();

        for (UUID userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
    }

    private User createPersistedUser(String emailPrefix, String rawPassword, UserRole role, boolean active) {
        String uniqueEmail = emailPrefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@precision.test";
        User saved = userRepository.save(User.builder()
                .email(uniqueEmail)
                .name("Usuário " + emailPrefix)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .active(active)
                .build());
        createdUserIds.add(saved.getId());
        return saved;
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresInSeconds", is(2592000)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseJson).get("token").asText();
    }

    @Test
    @DisplayName("Segurança 1: GET /api/v1/dashboard sem token JWT deve retornar HTTP 401")
    void testDashboardWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")
                        .param("date", LocalDate.now().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Segurança 2: Login válido retorna JWT funcional aceito em GET /api/v1/dashboard e /api/v1/auth/me")
    void testValidLoginIssuesWorkingJwtForDashboard() throws Exception {
        User user = createPersistedUser("client-valid", "SenhaForte#2026", UserRole.USER, true);
        String token = loginAndExtractToken(user.getEmail(), "SenhaForte#2026");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(user.getId().toString())))
                .andExpect(jsonPath("$.email", is(user.getEmail())))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.active", is(true)));

        mockMvc.perform(get("/api/v1/dashboard")
                        .param("date", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date", is(LocalDate.now().toString())));
    }

    @Test
    @DisplayName("Segurança 3: Login com senha errada ou e-mail inexistente retorna HTTP 401 com mensagem genérica")
    void testInvalidLoginReturnsGeneric401() throws Exception {
        User user = createPersistedUser("client-wrongpw", "SenhaCorreta#123", UserRole.USER, true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), "SenhaErrada!999"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("E-mail ou senha inválidos")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("naoexiste@precision.test", "QualquerSenha#1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("E-mail ou senha inválidos")));
    }

    @Test
    @DisplayName("Segurança 4 (Kill Switch): Usuário desativado (active=false) tem JWT válido imediatamente rejeitado com HTTP 401")
    void testDeactivatedUserWithValidJwtIsRejectedImmediately() throws Exception {
        User admin = createPersistedUser("admin-killswitch", "AdminForte#2026", UserRole.ADMIN, true);
        User client = createPersistedUser("client-killswitch", "ClienteForte#2026", UserRole.USER, true);

        String adminToken = loginAndExtractToken(admin.getEmail(), "AdminForte#2026");
        String clientToken = loginAndExtractToken(client.getEmail(), "ClienteForte#2026");

        // Confirma acesso inicial com o JWT do cliente
        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk());

        // Admin revoga acesso via kill switch PATCH /api/v1/admin/users/{id}/status
        mockMvc.perform(patch("/api/v1/admin/users/" + client.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminUpdateUserStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        // Mesmo JWT válido agora deve falhar imediatamente com HTTP 401
        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isUnauthorized());

        // Novo login também deve falhar com HTTP 401 genérico
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(client.getEmail(), "ClienteForte#2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("E-mail ou senha inválidos")));
    }

    @Test
    @DisplayName("Segurança 5 (RBAC Admin): ROLE_USER recebe HTTP 403 em /api/v1/admin/users e ROLE_ADMIN cria usuário (HTTP 201)")
    void testAdminEndpointsEnforceRoleAdmin() throws Exception {
        User regularUser = createPersistedUser("regular-rbac", "UserPass#2026", UserRole.USER, true);
        User adminUser = createPersistedUser("admin-rbac", "AdminPass#2026", UserRole.ADMIN, true);

        String userToken = loginAndExtractToken(regularUser.getEmail(), "UserPass#2026");
        String adminToken = loginAndExtractToken(adminUser.getEmail(), "AdminPass#2026");

        String newClientEmail = "provisioned-" + UUID.randomUUID().toString().substring(0, 8) + "@precision.test";
        AdminCreateUserRequest createReq = new AdminCreateUserRequest(
                newClientEmail,
                "NovaSenha#2026",
                "Cliente Provisionado",
                UserRole.USER
        );

        // ROLE_USER tentando criar usuário -> 403 Forbidden
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // ROLE_ADMIN criando usuário -> 201 Created
        String createdJson = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is(newClientEmail)))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID provisionedId = UUID.fromString(objectMapper.readTree(createdJson).get("id").asText());
        createdUserIds.add(provisionedId);

        // Usuário provisionado consegue autenticar imediatamente
        loginAndExtractToken(newClientEmail, "NovaSenha#2026");
    }

    @Test
    @DisplayName("Segurança 6 (Anti-IDOR + 404 Hardening): Isolamento entre usuários (403) e rota inexistente retorna 404 (nunca 500)")
    void testAntiIdorAndNotFoundHardening() throws Exception {
        User userA = createPersistedUser("idor-a", "SenhaUserA#2026", UserRole.USER, true);
        User userB = createPersistedUser("idor-b", "SenhaUserB#2026", UserRole.USER, true);

        Meal userAMeal = mealRepository.save(Meal.builder()
                .userId(userA.getId())
                .name("Refeição Privada User A")
                .orderIndex(1)
                .targetTime(LocalTime.of(8, 0))
                .build());
        createdMealIds.add(userAMeal.getId());

        String tokenB = loginAndExtractToken(userB.getEmail(), "SenhaUserB#2026");

        // User B tentando deletar refeição do User A -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/meals/" + userAMeal.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Rota inexistente autenticada deve retornar 404 Not Found (nunca 500)
        mockMvc.perform(get("/api/v1/rota-inexistente-teste")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));

        // Healthcheck permanece público
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }
}
