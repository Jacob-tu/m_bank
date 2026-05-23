package com.mbank.service;

import com.mbank.dto.request.ChangePasswordRequest;
import com.mbank.dto.request.LoginRequest;
import com.mbank.dto.request.RegisterRequest;
import com.mbank.dto.response.LoginResponse;
import com.mbank.dto.response.UserResponse;
import com.mbank.entity.Account;
import com.mbank.entity.User;
import com.mbank.exception.BusinessException;
import com.mbank.repository.AccountRepository;
import com.mbank.repository.UserRepository;
import com.mbank.security.JwtUtil;
import com.mbank.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 * 覆盖注册、登录、获取用户信息、修改密码四大业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private SmsService smsService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserServiceImpl userService;

    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "Password1";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedPassword";
    private static final String IP = "127.0.0.1";
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.test.token";

    @BeforeEach
    void setUp() {
        // 注入 @Value 字段
        ReflectionTestUtils.setField(userService, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(userService, "lockDurationMinutes", 15);
    }

    // ==================== 工厂方法 ====================

    private User buildUser(String status, int failedCount) {
        User user = new User();
        user.setId(1L);
        user.setPhone(PHONE);
        user.setPassword(ENCODED_PASSWORD);
        user.setStatus(status);
        user.setFailedLoginCount(failedCount);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

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
    @DisplayName("register - 用户注册")
    class RegisterTests {

        @Test
        @DisplayName("正常注册 - 返回用户信息，手机号脱敏")
        void register_withValidRequest_returnsUserResponse() {
            // Given
            RegisterRequest request = buildRegisterRequest(PHONE, "123456", PASSWORD);
            when(smsService.verifyCode(PHONE, "123456")).thenReturn(true);
            when(userRepository.existsByPhone(PHONE)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

            User savedUser = buildUser("NORMAL", 0);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(accountRepository.save(any(Account.class))).thenReturn(new Account());

            // When
            UserResponse response = userService.register(request, IP);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getPhone()).isEqualTo("138****8000");
            assertThat(response.getStatus()).isEqualTo("NORMAL");

            verify(auditLogService).log(any(), eq("REGISTER"), eq("USER"), contains("138****8000"), eq(IP));
        }

        @Test
        @DisplayName("验证码错误 - 抛出 400 BusinessException")
        void register_withWrongSmsCode_throws400() {
            // Given
            RegisterRequest request = buildRegisterRequest(PHONE, "000000", PASSWORD);
            when(smsService.verifyCode(PHONE, "000000")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.register(request, IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("验证码不正确")
                    .extracting("code").isEqualTo(400);
        }

        @Test
        @DisplayName("密码强度不足 - 纯数字密码抛出 400")
        void register_withWeakPassword_throws400() {
            // Given: 只含数字，不满足"至少两种字符类型"
            RegisterRequest request = buildRegisterRequest(PHONE, "123456", "12345678");
            when(smsService.verifyCode(PHONE, "123456")).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.register(request, IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("至少两种")
                    .extracting("code").isEqualTo(400);
        }

        @Test
        @DisplayName("手机号已注册 - 抛出 409 BusinessException")
        void register_withDuplicatePhone_throws409() {
            // Given
            RegisterRequest request = buildRegisterRequest(PHONE, "123456", PASSWORD);
            when(smsService.verifyCode(PHONE, "123456")).thenReturn(true);
            when(userRepository.existsByPhone(PHONE)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.register(request, IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("手机号已注册")
                    .extracting("code").isEqualTo(409);
        }

        @Test
        @DisplayName("注册时创建 Account（同一事务）")
        void register_createsAccountForUser() {
            // Given
            RegisterRequest request = buildRegisterRequest(PHONE, "123456", PASSWORD);
            when(smsService.verifyCode(PHONE, "123456")).thenReturn(true);
            when(userRepository.existsByPhone(PHONE)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

            User savedUser = buildUser("NORMAL", 0);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            userService.register(request, IP);

            // Then
            verify(accountRepository).save(any(Account.class));
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("login - 用户登录")
    class LoginTests {

        @Test
        @DisplayName("正常登录 - 返回 JWT Token")
        void login_withValidCredentials_returnsToken() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtUtil.generateToken(1L, PHONE)).thenReturn(TOKEN);

            // When
            LoginResponse response = userService.login(buildLoginRequest(PHONE, PASSWORD), IP);

            // Then
            assertThat(response.getToken()).isEqualTo(TOKEN);
            verify(auditLogService).log(eq(1L), eq("LOGIN"), eq("USER"), anyString(), eq(IP));
        }

        @Test
        @DisplayName("手机号未注册 - 返回模糊错误（防止手机号枚举）")
        void login_withUnknownPhone_throws401WithVagueMessage() {
            // Given
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.login(buildLoginRequest(PHONE, PASSWORD), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("手机号或密码错误")
                    .extracting("code").isEqualTo(401);

            // 即使手机号不存在也要记录审计日志
            verify(auditLogService).log(isNull(), eq("LOGIN_FAILED"), eq("USER"), anyString(), eq(IP));
        }

        @Test
        @DisplayName("密码错误 - 第 1 次，提示剩余次数")
        void login_withWrongPassword_firstAttempt_throws401() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.login(buildLoginRequest(PHONE, PASSWORD), IP))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(401);

            assertThat(user.getFailedLoginCount()).isEqualTo(1);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("密码连续错误 5 次 - 账户锁定 15 分钟")
        void login_withWrongPassword_5thAttempt_locksAccount() {
            // Given: 已失败 4 次
            User user = buildUser("NORMAL", 4);
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.login(buildLoginRequest(PHONE, PASSWORD), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("锁定15分钟")
                    .extracting("code").isEqualTo(403);

            assertThat(user.getStatus()).isEqualTo("LOCKED_TEMP");
            assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
            verify(auditLogService).log(eq(1L), eq("ACCOUNT_LOCKED"), eq("USER"), anyString(), eq(IP));
        }

        @Test
        @DisplayName("账户 FROZEN - 拒绝登录，返回 403")
        void login_withFrozenAccount_throws403() {
            // Given
            User user = buildUser("FROZEN", 0);
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));

            // When & Then
            assertThatThrownBy(() -> userService.login(buildLoginRequest(PHONE, PASSWORD), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("冻结")
                    .extracting("code").isEqualTo(403);
        }

        @Test
        @DisplayName("账户 LOCKED_TEMP 且锁定未过期 - 拒绝登录，返回 403")
        void login_withLockedAccount_notExpired_throws403() {
            // Given: 锁定 10 分钟后到期（还未到期）
            User user = buildUser("LOCKED_TEMP", 5);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));

            // When & Then
            assertThatThrownBy(() -> userService.login(buildLoginRequest(PHONE, PASSWORD), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("锁定15分钟")
                    .extracting("code").isEqualTo(403);
        }

        @Test
        @DisplayName("账户 LOCKED_TEMP 但锁定已过期 - 自动解锁并允许登录")
        void login_withLockedAccount_expired_autoUnlocksAndLogsIn() {
            // Given: 锁定时间已过
            User user = buildUser("LOCKED_TEMP", 5);
            user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtUtil.generateToken(1L, PHONE)).thenReturn(TOKEN);

            // When
            LoginResponse response = userService.login(buildLoginRequest(PHONE, PASSWORD), IP);

            // Then
            assertThat(response.getToken()).isEqualTo(TOKEN);
            assertThat(user.getStatus()).isEqualTo("NORMAL");
            assertThat(user.getFailedLoginCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("登录成功 - 重置失败计数")
        void login_success_resetsFailedCount() {
            // Given: 已失败 3 次
            User user = buildUser("NORMAL", 3);
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtUtil.generateToken(1L, PHONE)).thenReturn(TOKEN);

            // When
            userService.login(buildLoginRequest(PHONE, PASSWORD), IP);

            // Then
            assertThat(user.getFailedLoginCount()).isEqualTo(0);
            verify(userRepository).save(user);
        }
    }

    // ==================== getUserInfo ====================

    @Nested
    @DisplayName("getUserInfo - 获取用户信息")
    class GetUserInfoTests {

        @Test
        @DisplayName("用户存在 - 返回脱敏用户信息")
        void getUserInfo_withExistingUser_returnsMaskedResponse() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // When
            UserResponse response = userService.getUserInfo(1L);

            // Then
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getPhone()).isEqualTo("138****8000");
        }

        @Test
        @DisplayName("用户不存在 - 抛出 404 BusinessException")
        void getUserInfo_withNonExistentUser_throws404() {
            // Given
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.getUserInfo(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在")
                    .extracting("code").isEqualTo(404);
        }
    }

    // ==================== changePassword ====================

    @Nested
    @DisplayName("changePassword - 修改密码")
    class ChangePasswordTests {

        private ChangePasswordRequest buildChangePasswordRequest(String oldPwd, String newPwd) {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword(oldPwd);
            req.setNewPassword(newPwd);
            return req;
        }

        @Test
        @DisplayName("正常修改密码 - passwordChangedAt 更新，审计日志记录")
        void changePassword_withValidRequest_updatesPasswordChangedAt() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass1", ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncoder.matches("NewPass2", ENCODED_PASSWORD)).thenReturn(false);
            when(passwordEncoder.encode("NewPass2")).thenReturn("$2a$10$newEncodedPassword");

            // When
            userService.changePassword(1L, buildChangePasswordRequest("OldPass1", "NewPass2"), IP);

            // Then
            assertThat(user.getPasswordChangedAt()).isNotNull();
            assertThat(user.getPasswordChangedAt()).isBeforeOrEqualTo(LocalDateTime.now());
            verify(userRepository).save(user);
            verify(auditLogService).log(eq(1L), eq("CHANGE_PASSWORD"), eq("USER"), anyString(), eq(IP));
        }

        @Test
        @DisplayName("旧密码错误 - 抛出 400 BusinessException")
        void changePassword_withWrongOldPassword_throws400() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongOld1", ENCODED_PASSWORD)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.changePassword(
                    1L, buildChangePasswordRequest("WrongOld1", "NewPass2"), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("旧密码不正确")
                    .extracting("code").isEqualTo(400);
        }

        @Test
        @DisplayName("新密码强度不足（纯小写）- 抛出 400")
        void changePassword_withWeakNewPassword_throws400() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass1", ENCODED_PASSWORD)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.changePassword(
                    1L, buildChangePasswordRequest("OldPass1", "newpassword"), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("至少两种")
                    .extracting("code").isEqualTo(400);
        }

        @Test
        @DisplayName("新旧密码相同 - 抛出 400 BusinessException")
        void changePassword_withSamePassword_throws400() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password1", ENCODED_PASSWORD)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.changePassword(
                    1L, buildChangePasswordRequest("Password1", "Password1"), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("新密码不能与旧密码相同")
                    .extracting("code").isEqualTo(400);
        }

        @Test
        @DisplayName("用户不存在 - 抛出 404")
        void changePassword_withNonExistentUser_throws404() {
            // Given
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.changePassword(
                    99L, buildChangePasswordRequest("OldPass1", "NewPass2"), IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在")
                    .extracting("code").isEqualTo(404);
        }

        @Test
        @DisplayName("修改密码后新密码正确编码并保存")
        void changePassword_encodesNewPassword() {
            // Given
            User user = buildUser("NORMAL", 0);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass1", ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncoder.matches("NewPass2", ENCODED_PASSWORD)).thenReturn(false);
            when(passwordEncoder.encode("NewPass2")).thenReturn("$2a$10$newHash");

            // When
            userService.changePassword(1L, buildChangePasswordRequest("OldPass1", "NewPass2"), IP);

            // Then
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$newHash");
        }
    }
}
