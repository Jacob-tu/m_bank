package com.mbank.service;

/**
 * 短信服务接口（SF-01）
 * 解耦短信发送实现，支持 Mock 和真实网关切换
 */
public interface SmsService {

    /**
     * 验证短信验证码
     *
     * @param phone   手机号
     * @param smsCode 用户输入的验证码
     * @return true 表示验证通过
     */
    boolean verifyCode(String phone, String smsCode);
}
