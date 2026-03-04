package com.authservice.controller;

import com.authservice.dto.request.LoginRequest;
import com.authservice.dto.request.RefreshTokenRequest;
import com.authservice.dto.request.RegisterRequest;
import com.authservice.dto.request.RoleUpdateRequest;
import com.authservice.dto.response.AuthResponse;
import com.authservice.domain.entity.Role;
import com.authservice.domain.entity.UserEntity;
import com.authservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("authdb_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // --- Registration ---

    @Test
    @DisplayName("POST /api/v1/auth/register - success")
    void register_shouldReturnTokens() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "StrongPass1!", "Test User");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - duplicate email returns 409")
    void register_duplicateEmail_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest("dup@example.com", "StrongPass1!", "User");

        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same email
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - validation errors")
    void register_invalidInput_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "short", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    // --- Login ---

    @Test
    @DisplayName("POST /api/v1/auth/login - success")
    void login_shouldReturnTokens() throws Exception {
        // Register first
        RegisterRequest regRequest = new RegisterRequest("login@example.com", "StrongPass1!", "Login User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Login
        LoginRequest loginRequest = new LoginRequest("login@example.com", "StrongPass1!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - wrong password returns 401")
    void login_wrongPassword_shouldReturn401() throws Exception {
        RegisterRequest regRequest = new RegisterRequest("wrongpw@example.com", "StrongPass1!", "User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("wrongpw@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - non-existent email returns same error")
    void login_nonExistentEmail_shouldReturn401SameMessage() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nobody@example.com", "Password123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    // --- Refresh ---

    @Test
    @DisplayName("POST /api/v1/auth/refresh - rotates tokens")
    void refresh_shouldReturnNewTokens() throws Exception {
        // Register and get tokens
        RegisterRequest regRequest = new RegisterRequest("refresh@example.com", "StrongPass1!", "User");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);

        // Refresh
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(authResponse.refreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - reuse detection")
    void refresh_reuseDetection_shouldRevokeAll() throws Exception {
        // Register and get tokens
        RegisterRequest regRequest = new RegisterRequest("reuse@example.com", "StrongPass1!", "User");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth1 = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        String originalRefreshToken = auth1.refreshToken();

        // Use refresh token once (valid)
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(originalRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk());

        // Reuse the same refresh token (should trigger reuse detection)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());
    }

    // --- Logout ---

    @Test
    @DisplayName("POST /api/v1/auth/logout - success")
    void logout_shouldReturnSuccess() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("any-token-value");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    // --- Protected endpoints ---

    @Test
    @DisplayName("GET /api/v1/users/me - returns current user")
    void getMe_shouldReturnUser() throws Exception {
        // Register and get access token
        RegisterRequest regRequest = new RegisterRequest("me@example.com", "StrongPass1!", "Me User");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.name").value("Me User"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me - without token returns 401")
    void getMe_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // --- Admin endpoints ---

    @Test
    @DisplayName("GET /api/v1/admin/users - admin can list users")
    void adminListUsers_shouldWork() throws Exception {
        // Register a user, then promote to admin
        RegisterRequest regRequest = new RegisterRequest("admin@example.com", "StrongPass1!", "Admin");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        // Promote user to ADMIN directly in DB
        UserEntity admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        // Re-login to get token with ADMIN role
        LoginRequest loginRequest = new LoginRequest("admin@example.com", "StrongPass1!");
        result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - regular user gets 403")
    void adminListUsers_asUser_shouldReturn403() throws Exception {
        RegisterRequest regRequest = new RegisterRequest("user403@example.com", "StrongPass1!", "User");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/role - admin can change roles")
    void adminUpdateRole_shouldWork() throws Exception {
        // Create admin
        RegisterRequest adminReg = new RegisterRequest("roleadmin@example.com", "StrongPass1!", "Admin");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReg)))
                .andExpect(status().isCreated());

        UserEntity admin = userRepository.findByEmail("roleadmin@example.com").orElseThrow();
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        // Create a regular user
        RegisterRequest userReg = new RegisterRequest("target@example.com", "StrongPass1!", "Target");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReg)))
                .andExpect(status().isCreated());

        UserEntity target = userRepository.findByEmail("target@example.com").orElseThrow();

        // Login as admin
        LoginRequest loginRequest = new LoginRequest("roleadmin@example.com", "StrongPass1!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);

        // Update role
        RoleUpdateRequest roleReq = new RoleUpdateRequest(Role.ADMIN);
        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/role")
                        .header("Authorization", "Bearer " + authResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/deactivate - admin can deactivate")
    void adminDeactivateUser_shouldWork() throws Exception {
        // Create admin
        RegisterRequest adminReg = new RegisterRequest("deactadmin@example.com", "StrongPass1!", "Admin");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReg)))
                .andExpect(status().isCreated());

        UserEntity admin = userRepository.findByEmail("deactadmin@example.com").orElseThrow();
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        // Create target user
        RegisterRequest userReg = new RegisterRequest("deactivate-me@example.com", "StrongPass1!", "DeactUser");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReg)))
                .andExpect(status().isCreated());

        UserEntity target = userRepository.findByEmail("deactivate-me@example.com").orElseThrow();

        // Login as admin
        LoginRequest loginRequest = new LoginRequest("deactadmin@example.com", "StrongPass1!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);

        // Deactivate
        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }
}
