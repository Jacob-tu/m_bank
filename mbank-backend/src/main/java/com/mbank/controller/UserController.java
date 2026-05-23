package com.mbank.controller;

import com.mbank.dto.request.ChangePasswordRequest;
import com.mbank.dto.response.ApiResponse;
import com.mbank.dto.response.UserResponse;
import com.mbank.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 * 处理用户信息查询和密码修改接口（需要认证）
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取当前用户信息
     * GET /api/v1/users/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal Long userId) {
        UserResponse response = userService.getUserInfo(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 修改密码
     * PUT /api/v1/users/me/password
     */
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        userService.changePassword(userId, request, ip);
        return ResponseEntity.ok(ApiResponse.success("密码修改成功，请重新登录"));
    }

    /**
     * 提取客户端 IP（兼容代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
