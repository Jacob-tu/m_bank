package com.mbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户注册请求
 */
@Getter
@Setter
public class RegisterRequest {

    /** 手机号：11位中国大陆手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 短信验证码：6位数字 */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String smsCode;

    /** 登录密码：8-20位，包含大写字母、小写字母、数字中的至少两种 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须在8到20位之间")
    private String password;
}
