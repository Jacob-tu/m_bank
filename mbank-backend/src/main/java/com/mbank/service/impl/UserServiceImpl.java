package com.mbank.service.impl;

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
import com.mbank.service.AuditLogService;
import com.mbank.service.SmsService;
import com.mbank.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 用户服务实现类
 * 处理用户注册、登录、密码管理等业务逻辑
 */
@Service
public class UserServiceImpl implements UserService {

    /** 密码强度校验：包含大写字母、小写字母、数字中的至少两种 */
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SmsService smsService;
    private final AuditLogService auditLogService;

    @Value("${mbank.security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${mbank.security.lock-duration-minutes:15}")
    private int lockDurationMinutes;

    public UserServiceImpl(UserRepository userRepository,
                           AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           SmsService smsService,
                           AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.smsService = smsService;
        this.auditLogService = auditLogService;
    }

    /**
     * 用户注册
     * 1. 验证短信验证码
     * 2. 校验密码强度
     * 3. 检查手机号唯一性
     * 4. 创建 User 和 Account（同一事务）
     * 5. 记录审计日志
     *
     * @param request 注册请求
     * @param ip      客户端 IP
     * @return 用户信息
     */
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request, String ip) {
        // 1. 验证短信验证码
        if (!smsService.verifyCode(request.getPhone(), request.getSmsCode())) {
            throw new BusinessException(400, "验证码不正确");
        }

        // 2. 校验密码强度
        validatePasswordStrength(request.getPassword());

        // 3. 检查手机号唯一性
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(409, "手机号已注册");
        }

        // 4. 创建用户
        User user = new User();
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus("NORMAL");
        user.setMobileVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        // 4. 创建资金账户（同一事务）
        Account account = new Account();
        account.setUserId(user.getId());
        accountRepository.save(account);

        // 5. 记录审计日志
        String detail = String.format("{\"phone\":\"%s\"}", maskPhone(request.getPhone()));
        auditLogService.log(user.getId(), "REGISTER", "USER", detail, ip);

        return UserResponse.from(user);
    }

    /**
     * 用户登录
     * 1. 查询用户（未找到返回模糊错误）
     * 2. 检查用户状态（FROZEN 拒绝；LOCKED_TEMP 检查是否过期）
     * 3. 验证密码
     * 4. 生成 JWT Token
     * 5. 记录审计日志
     *
     * @param request 登录请求
     * @param ip      客户端 IP
     * @return JWT Token
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String ip) {
        // 1. 根据手机号查询用户（未找到返回模糊错误，防止手机号枚举）
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> {
                    auditLogService.log(null, "LOGIN_FAILED", "USER",
                            String.format("{\"phone\":\"%s\"}", maskPhone(request.getPhone())), ip);
                    return new BusinessException(401, "手机号或密码错误");
                });

        // 2. 检查用户状态
        if ("FROZEN".equals(user.getStatus())) {
            throw new BusinessException(403, "账户已冻结，请联系客服");
        }

        if ("LOCKED_TEMP".equals(user.getStatus())) {
            if (user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil())) {
                throw new BusinessException(403, "密码连续错误5次，账户已锁定15分钟");
            }
            // 锁定已过期，自动解锁
            user.setStatus("NORMAL");
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }

        // 3. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int attempts = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(attempts);

            if (attempts >= maxLoginAttempts) {
                // 达到阈值，锁定账户
                user.setStatus("LOCKED_TEMP");
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
                userRepository.save(user);
                auditLogService.log(user.getId(), "ACCOUNT_LOCKED", "USER",
                        String.format("{\"userId\":%d,\"lockedUntil\":\"%s\"}", user.getId(), user.getLockedUntil()), ip);
                throw new BusinessException(403, "密码连续错误5次，账户已锁定15分钟");
            }

            userRepository.save(user);
            int remaining = maxLoginAttempts - attempts;
            auditLogService.log(user.getId(), "LOGIN_FAILED", "USER",
                    String.format("{\"phone\":\"%s\",\"attempt\":%d}", maskPhone(user.getPhone()), attempts), ip);
            throw new BusinessException(401, String.format("手机号或密码错误，还剩%d次机会", remaining));
        }

        // 4. 登录成功：重置失败次数，生成 Token
        user.setFailedLoginCount(0);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getPhone());

        // 5. 记录登录成功审计日志
        auditLogService.log(user.getId(), "LOGIN", "USER",
                String.format("{\"userId\":%d}", user.getId()), ip);

        return new LoginResponse(token);
    }

    /**
     * 获取当前用户信息
     *
     * @param userId 用户ID
     * @return 用户信息（手机号脱敏）
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        return UserResponse.from(user);
    }

    /**
     * 修改密码
     * 1. 验证旧密码
     * 2. 校验新密码强度
     * 3. 校验新旧密码不同
     * 4. 更新密码和 passwordChangedAt（使所有旧 Token 失效）
     * 5. 记录审计日志
     *
     * @param userId  当前用户ID
     * @param request 修改密码请求
     * @param ip      客户端 IP
     */
    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        // 1. 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "旧密码不正确");
        }

        // 2. 校验新密码强度
        validatePasswordStrength(request.getNewPassword());

        // 3. 校验新旧密码不同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(400, "新密码不能与旧密码相同");
        }

        // 4. 更新密码和 passwordChangedAt（使所有已签发 Token 失效）
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // 5. 记录审计日志
        auditLogService.log(userId, "CHANGE_PASSWORD", "USER",
                String.format("{\"userId\":%d}", userId), ip);
    }

    /**
     * 校验密码强度：>=8 位，包含大写字母、小写字母、数字中的至少两种
     */
    private void validatePasswordStrength(String password) {
        int typeCount = 0;
        if (UPPER.matcher(password).find()) typeCount++;
        if (LOWER.matcher(password).find()) typeCount++;
        if (DIGIT.matcher(password).find()) typeCount++;
        if (typeCount < 2) {
            throw new BusinessException(400, "密码须包含大写字母、小写字母、数字中的至少两种");
        }
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
