package com.mbank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试
 * 使用 H2 内存数据库，测试 POST /api/v1/auth/register 和 POST /api/v1/auth/login
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController 集成测试")
class AuthControllerTest {

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

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== 工厂方法 ====================

    private RegisterRequest buildRegisterRequest(String phone, String smsCode, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setPhone(phone);
        req.setSmsCode(smsCode);
        req.setPassword(password);
        return req;
    }

    private LoginRequest buildLoginRequest(String phone, String password) {
        LoginRequest req = new LoginRequest();
        req.setPhone(phone);
        req.setPassword(password);
        return req;
    }

    // ==================== register ====================

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("正常注册 - 返回 201，手机号脱敏")
        void register_withValidRequest_returns201() throws Exception {
            RegisterRequest request = buildRegisterRequest("13800138001", "123456", "Password1");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    // ApiResponse.code 固定为 200（业务成功），HTTP status 才是 201
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("注册成功"))
                    .andExpect(jsonPath("$.data.phone").value("138****8001"))
                    .andExpect(jsonPath("$.data.status").value("NORMAL"));
        }

        @Test
        @DisplayName("验证码错误 - 返回 400")
        void register_withWrongSmsCode_returns400() throws Exception {
            RegisterRequest request = buildRegisterRequest("13800138002", "999999", "Password1");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("验证码不正确"));
        }

        @Test
        @DisplayName("手机号格式错误 - 返回 400（参数校验）")
        void register_withInvalidPhone_returns400() throws Exception {
            RegisterRequest request = buildRegisterRequest("123", "123456", "Password1");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("密码为空 - 返回 400（参数校验）")
        void register_withBlankPassword_returns400() throws Exception {
            RegisterRequest request = buildRegisterRequest("13800138003", "123456", "");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("密码过短（7位）- 返回 400")
        void register_withTooShortPassword_returns400() throws Exception {
            RegisterRequest request = buildRegisterRequest("13800138004", "123456", "Pass1");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("请求体为空 - 返回 400")
        void register_withEmptyBody_returns400() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("重复注册同一手机号 - 返回 409")
        void register_withDuplicatePhone_returns409() throws Exception {
            // 第一次注册
            RegisterRequest request = buildRegisterRequest("13800138005", "123456", "Password1");
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // 第二次注册同一手机号
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        /**
         * 先注册用户，再测试登录
         */
        private void registerUser(String phone, String password) throws Exception {
            RegisterRequest req = buildRegisterRequest(phone, "123456", password);
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("正常登录 - 返回 200 和 JWT Token")
        void login_withValidCredentials_returns200WithToken() throws Exception {
            registerUser("13800139001", "Password1");

            LoginRequest request = buildLoginRequest("13800139001", "Password1");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").isNotEmpty());
        }

        @Test
        @DisplayName("密码错误 - 返回 401，消息包含错误提示")
        void login_withWrongPassword_returns401() throws Exception {
            registerUser("13800139002", "Password1");

            LoginRequest request = buildLoginRequest("13800139002", "WrongPass1");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    // 当前实现附加剩余次数提示（如"手机号或密码错误，还剩4次机会"）
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("手机号或密码错误")));
        }

        @Test
        @DisplayName("手机号未注册 - 返回 401（模糊错误）")
        void login_withUnknownPhone_returns401WithVagueMessage() throws Exception {
            LoginRequest request = buildLoginRequest("13900000001", "Password1");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("手机号或密码错误"));
        }

        @Test
        @DisplayName("手机号格式错误 - 返回 400（参数校验）")
        void login_withInvalidPhone_returns400() throws Exception {
            LoginRequest request = buildLoginRequest("not-a-phone", "Password1");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("密码为空 - 返回 400（参数校验）")
        void login_withBlankPassword_returns400() throws Exception {
            LoginRequest request = buildLoginRequest("13800139003", "");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("连续 5 次密码错误 - 账户锁定，返回 403")
        void login_5wrongAttempts_accountLocked_returns403() throws Exception {
            registerUser("13800139004", "CorrectPass1");

            LoginRequest wrongReq = buildLoginRequest("13800139004", "WrongPass1");

            // 前 4 次
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(wrongReq)))
                        .andExpect(status().isUnauthorized());
            }

            // 第 5 次 - 账户锁定
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongReq)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("密码连续错误5次，账户已锁定15分钟"));
        }
    }
}
