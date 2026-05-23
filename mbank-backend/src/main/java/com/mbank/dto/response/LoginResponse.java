package com.mbank.dto.response;

import lombok.Getter;

/**
 * 登录成功响应
 */
@Getter
public class LoginResponse {

    private final String token;

    public LoginResponse(String token) {
        this.token = token;
    }
}
