package com.moonliter.auth.application;

import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

    public String toE164(String value) {
        String compact = compact(value);
        if (compact.startsWith("+82") && compact.length() >= 11 && compact.length() <= 16) {
            return compact;
        }
        if (compact.startsWith("0") && compact.length() >= 10 && compact.length() <= 11) {
            return "+82" + compact.substring(1);
        }
        throw new IllegalArgumentException("국가번호를 포함한 한국 전화번호를 입력해주세요. 예: +821012345678");
    }

    public String toSmsReceiver(String phoneE164) {
        if (!phoneE164.startsWith("+82")) {
            throw new IllegalArgumentException("SMS 인증은 한국 전화번호만 지원합니다.");
        }
        return "0" + phoneE164.substring(3);
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s-]", "");
    }
}
