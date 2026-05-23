package com.mbank.dto.response;

import com.mbank.entity.User;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户信息响应
 * 手机号做脱敏处理：138****8000
 */
@Getter
public class UserResponse {

    private final Long id;
    private final String phone;
    private final String status;
    private final LocalDateTime createdAt;

    private UserResponse(Long id, String phone, String status, LocalDateTime createdAt) {
        this.id = id;
        this.phone = phone;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * 从 User 实体转换，手机号自动脱敏
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                maskPhone(user.getPhone()),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    /**
     * 手机号脱敏：138****8000（第4-7位替换为 ****）
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
