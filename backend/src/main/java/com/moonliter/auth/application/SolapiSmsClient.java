package com.moonliter.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SolapiSmsClient {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final String apiKey;
    private final String apiSecret;
    private final String sender;
    private final Clock clock;

    public SolapiSmsClient(
        RestClient.Builder restClientBuilder,
        @Value("${moonliter.sms.solapi.base-url:https://api.solapi.com}") String baseUrl,
        @Value("${moonliter.sms.solapi.api-key}") String apiKey,
        @Value("${moonliter.sms.solapi.api-secret}") String apiSecret,
        @Value("${moonliter.sms.solapi.sender}") String sender,
        Clock clock
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.sender = sender;
        this.clock = clock;
    }

    public void sendVerificationCode(String receiver, String code) {
        Map<String, Object> body = Map.of(
            "messages",
            List.of(Map.of(
                "to", receiver,
                "from", sender,
                "type", "SMS",
                "text", "[문라이터] 인증번호는 " + code + " 입니다."
            ))
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
            .uri("/messages/v4/send-many/detail")
            .header("Authorization", authorizationHeader())
            .body(body)
            .retrieve()
            .body(Map.class);

        Object failedMessageList = response == null ? null : response.get("failedMessageList");
        if (failedMessageList instanceof List<?> failedMessages && !failedMessages.isEmpty()) {
            throw new IllegalStateException("문자 발송에 실패했습니다.");
        }
    }

    private String authorizationHeader() {
        String date = Instant.now(clock).toString();
        String salt = createSalt();
        String signature = hmacSha256(date + salt);
        return "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s"
            .formatted(apiKey, date, salt, signature);
    }

    private String createSalt() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SOLAPI 인증 서명을 생성할 수 없습니다.");
        }
    }
}
