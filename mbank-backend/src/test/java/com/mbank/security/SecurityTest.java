package com.mbank.security;

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
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 安全测试套件
 * 覆盖：未认证访问、Token 失效、CORS、鉴权边界
 * 注意：Spring Security 默认对无认证访问返回 403（AccessDeniedException），
 * 只有携带了无效 Token（JWT Filter 拒绝）时才返回 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("安全测试套件")
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String ME_URL = "/api/v1/users/me";

    private String validToken;

    @BeforeEach
    void setUp() throws Exception {
        // 清理数据，避免测试间干扰
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setPhone("13822222222");
        registerReq.setSmsCode("123456");
        registerReq.setPassword("SecurityTest1");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest();
        loginReq.setPhone("13822222222");
        loginReq.setPassword("SecurityTest1");

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        validToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== 未认证访问 ====================

    @Nested
    @DisplayName("未认证访问保护接口")
    class UnauthenticatedAccessTests {

        @Test
        @DisplayName("未携带 Token 访问受保护接口 - 返回 403（Spring Security 默认行为）")
        void accessProtectedEndpoint_withoutToken_returns403() throws Exception {
            // Spring Security 默认：无认证上下文 → AccessDeniedException → 403
            // 如需 401 需配置 AuthenticationEntryPoint
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Token 完全伪造 - 返回 401（JWT Filter 主动拒绝）")
        void accessProtectedEndpoint_withFakeToken_returns401() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer fake.jwt.token.here"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token 签名篡改 - 返回 401（JWT Filter 主动拒绝）")
        void accessProtectedEndpoint_withTamperedSignature_returns401() throws Exception {
            // 取有效 token 修改最后几个字符（破坏签名）
            String tampered = validToken.substring(0, validToken.length() - 10) + "tampered12";
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer " + tampered))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Authorization 头无 Bearer 前缀 - 返回 403（JWT Filter 跳过，Security 默认 403）")
        void accessProtectedEndpoint_withoutBearerPrefix_returns403() throws Exception {
            // JWT Filter 仅处理 "Bearer " 前缀的请求，无前缀时跳过 → 无认证上下文 → 403
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", validToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Authorization 头为空字符串 - 返回 403（JWT Filter 跳过，Security 默认 403）")
        void accessProtectedEndpoint_withEmptyAuthHeader_returns403() throws Exception {
            // 空值头：JWT Filter 跳过 → 无认证上下文 → 403
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", ""))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== 公开接口无需认证 ====================

    @Nested
    @DisplayName("公开接口（/api/v1/auth/**）无需认证")
    class PublicEndpointTests {

        @Test
        @DisplayName("/api/v1/auth/register 不需要认证即可访问")
        void registerEndpoint_withoutToken_isAccessible() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setPhone("13833333333");
            req.setSmsCode("123456");
            req.setPassword("OpenPass1");

            // 不携带 Token 也能访问
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("/api/v1/auth/login 不需要认证即可访问")
        void loginEndpoint_withoutToken_isAccessible() throws Exception {
            // 该手机号未注册，接口本身可访问，返回业务错误 401
            LoginRequest req = new LoginRequest();
            req.setPhone("13899999999");
            req.setPassword("TestPass1");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("手机号或密码错误"));
        }
    }

    // ==================== CORS 策略 ====================

    @Nested
    @DisplayName("CORS 策略验证")
    class CorsTests {

        @Test
        @DisplayName("来自 localhost 的 OPTIONS 预检请求 - 返回 200")
        void preflightRequest_fromLocalhost_returns200() throws Exception {
            mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(ME_URL)
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "GET")
                            .header("Access-Control-Request-Headers", "Authorization"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("来自 localhost 的请求 - 响应包含 CORS 头")
        void request_fromLocalhost_hasCorsHeaders() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Origin", "http://localhost:3000")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Access-Control-Allow-Origin"));
        }
    }

    // ==================== 响应格式 ====================

    @Nested
    @DisplayName("统一响应格式验证")
    class ResponseFormatTests {

        @Test
        @DisplayName("成功响应包含 code/message/data 三个字段")
        void successResponse_hasRequiredFields() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @DisplayName("错误响应包含 code/message，data 字段因 @JsonInclude(NON_NULL) 被省略")
        void errorResponse_hasRequiredFields() throws Exception {
            // ApiResponse 使用 @JsonInclude(NON_NULL)，错误时 data=null 不序列化到 JSON
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13899999999\",\"password\":\"TestPass1\"}"))
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("JWT 过滤器 401 响应格式为 JSON")
        void jwtFilter_401_returnsJsonFormat() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer invalid.token"))
                    .andExpect(status().isUnauthorized())
                    // 使用 contentTypeCompatibleWith 兼容 application/json;charset=UTF-8
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
