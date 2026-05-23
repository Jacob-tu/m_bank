package com.mbank.service;

import com.mbank.dto.request.ChangePasswordRequest;
import com.mbank.dto.request.LoginRequest;
import com.mbank.dto.request.RegisterRequest;
import com.mbank.dto.response.LoginResponse;
import com.mbank.dto.response.UserResponse;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param request 注册请求（手机号、验证码、密码）
     * @param ip      客户端 IP
     * @return 注册成功的用户信息
     */
    UserResponse register(RegisterRequest request, String ip);

    /**
     * 用户登录
     *
     * @param request 登录请求（手机号、密码）
     * @param ip      客户端 IP
     * @return JWT Token
     */
    LoginResponse login(LoginRequest request, String ip);

    /**
     * 获取当前用户信息
     *
     * @param userId 用户ID（从 JWT 中提取）
     * @return 用户信息（手机号脱敏）
     */
    UserResponse getUserInfo(Long userId);

    /**
     * 修改密码
     *
     * @param userId  当前用户ID
     * @param request 修改密码请求
     * @param ip      客户端 IP
     */
    void changePassword(Long userId, ChangePasswordRequest request, String ip);
}
