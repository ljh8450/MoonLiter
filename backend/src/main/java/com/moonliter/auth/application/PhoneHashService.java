package com.moonliter.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhoneHashService {

    private final String secret;

    public PhoneHashService(@Value("${moonliter.phone.hash-secret}") String secret) {
        this.secret = secret;
    }

    public String hash(String phoneE164) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(phoneE164.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("전화번호 해시를 생성할 수 없습니다.");
        }
    }
}
