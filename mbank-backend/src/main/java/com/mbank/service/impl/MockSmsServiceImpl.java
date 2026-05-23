package com.mbank.service.impl;

import com.mbank.service.SmsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 短信服务 Mock 实现
 * 当 mbank.sms.mock-enabled=true 时激活
 * Mock 模式下接受固定验证码，用于开发和测试环境
 */
@Service
@ConditionalOnProperty(name = "mbank.sms.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockSmsServiceImpl implements SmsService {

    @Value("${mbank.sms.valid-code:123456}")
    private String validCode;

    /**
     * Mock 实现：只接受配置中的固定验证码
     */
    @Override
    public boolean verifyCode(String phone, String smsCode) {
        return validCode.equals(smsCode);
    }
}
