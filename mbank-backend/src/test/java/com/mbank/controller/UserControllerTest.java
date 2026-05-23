package com.mbank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mbank.dto.request.ChangePasswordRequest;
import com.mbank.dto.request.LoginRequest;
import com.mbank.dto.request.RegisterRequest;
import com.mbank.repository.AccountRepository;
import com.mbank.repository.AuditLogRepository;
import com.mbank.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 集成测试
 * 覆盖 GET /api/v1/users/me 和 PUT /api/v1/users/me/password
 * 重点测试认证鉴权和业务逻辑
 * 注意：
 *   - 不使用 @Transactional，避免 MockMvc + Spring Security + PUT 的兼容性问题
 *   - Spring Security 默认：无 Token → 403；携带无效 Token（JWT Filter 拒绝）→ 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UserController 集成测试")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String ME_URL = "/api/v1/users/me";
    private static final String CHANGE_PASSWORD_URL = "/api/v1/users/me/password";

    private String validToken;
    private static final String USER_PHONE = "13811111111";
    private static final String USER_PASSWORD = "TestPass1";

    @BeforeEach
    void setUp() throws Exception {
        // 清理数据，避免测试间干扰
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // 注册并登录，获取有效 Token
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setPhone(USER_PHONE);
        registerReq.setSmsCode("123456");
        registerReq.setPassword(USER_PASSWORD);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest();
        loginReq.setPhone(USER_PHONE);
        loginReq.setPassword(USER_PASSWORD);

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        validToken = objectMapper.readTree(responseBody)
                .path("data").path("token").asText();
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== GET /api/v1/users/me ====================

    @Nested
    @DisplayName("GET /api/v1/users/me - 获取当前用户信息")
    class GetMeTests {

        @Test
        @DisplayName("携带有效 Token - 返回 200，手机号脱敏")
        void getMe_withValidToken_returns200() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.phone").value("138****1111"))
                    .andExpect(jsonPath("$.data.status").value("NORMAL"));
        }

        @Test
        @DisplayName("未携带 Token - 返回 403（Spring Security 默认行为）")
        void getMe_withoutToken_returns401() throws Exception {
            // Spring Security 默认：无认证上下文 → AccessDeniedException → 403
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("携带无效 Token - 返回 401（JWT Filter 主动拒绝）")
        void getMe_withInvalidToken_returns401() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer invalid.jwt.token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token 无 Bearer 前缀 - 返回 403（JWT Filter 跳过，Security 默认 403）")
        void getMe_withMalformedAuthHeader_returns401() throws Exception {
            // JWT Filter 仅处理 "Bearer " 前缀，无前缀则跳过 → 无认证上下文 → 403
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", validToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== PUT /api/v1/users/me/password ====================

    @Nested
    @DisplayName("PUT /api/v1/users/me/password - 修改密码")
    class ChangePasswordTests {

        private ChangePasswordRequest buildChangeRequest(String oldPwd, String newPwd) {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword(oldPwd);
            req.setNewPassword(newPwd);
            return req;
        }

        @Test
        @DisplayName("正常修改密码 - 返回 200")
        void changePassword_withValidRequest_returns200() throws Exception {
            ChangePasswordRequest request = buildChangeRequest(USER_PASSWORD, "NewPass2");

            mockMvc.perform(put(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("修改密码后旧 Token 失效 - 再次请求返回 401")
        void changePassword_oldTokenInvalidatedAfterChange() throws Exception {
            // 修改密码
            ChangePasswordRequest request = buildChangeRequest(USER_PASSWORD, "NewPass2");
            mockMvc.perform(put(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 使用旧 Token 访问 - 应该被拒绝（MF-01）
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("未认证修改密码 - 返回 403（Spring Security 默认行为）")
        void changePassword_withoutToken_returns401() throws Exception {
            ChangePasswordRequest request = buildChangeRequest(USER_PASSWORD, "NewPass2");
            // 无 Token → 无认证上下文 → Spring Security 默认 403
            mockMvc.perform(put(CHANGE_PASSWORD_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("旧密码错误 - 返回 400")
        void changePassword_withWrongOldPassword_returns400() throws Exception {
            ChangePasswordRequest request = buildChangeRequest("WrongPass1", "NewPass2");

            mockMvc.perform(put(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("旧密码不正确"));
        }

        @Test
        @DisplayName("新密码强度不足 - 返回 400")
        void changePassword_withWeakNewPassword_returns400() throws Exception {
            ChangePasswordRequest request = buildChangeRequest(USER_PASSWORD, "weakpassword");

            mockMvc.perform(put(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("新旧密码相同 - 返回 400")
        void changePassword_withSamePassword_returns400() throws Exception {
            ChangePasswordRequest request = buildChangeRequest(USER_PASSWORD, USER_PASSWORD);

            mockMvc.perform(put(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("新密码不能与旧密码相同"));
        }

        @Test
        @DisplayName("新密码为空 - 返回 400（参数校验）")
        void changePassword_withBlankNewPassword_returns400() throws Exception {
            ChangePasswordRequest request = buildChangeRequest(USER_PASSWORD, "");

            mockMvc.perform(put(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
