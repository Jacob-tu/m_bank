package com.mbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改密码请求
 */
@Getter
@Setter
public class ChangePasswordRequest {

    /** 旧密码 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码：8-20位，包含大写字母、小写字母、数字中的至少两种 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须在8到20位之间")
    private String newPassword;
}
