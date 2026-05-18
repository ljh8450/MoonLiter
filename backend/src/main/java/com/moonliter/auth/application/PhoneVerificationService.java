package com.moonliter.auth.application;

import com.moonliter.auth.domain.PhoneVerificationCode;
import com.moonliter.auth.repository.PhoneVerificationCodeRepository;
import com.moonliter.user.domain.AppUser;
import com.moonliter.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneVerificationService {

    private static final int OTP_BOUND = 1_000_000;
    private static final int MAX_ATTEMPTS = 5;
    private static final long EXPIRES_IN_SECONDS = 5 * 60;

    private final UserRepository userRepository;
    private final PhoneVerificationCodeRepository phoneVerificationCodeRepository;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final PhoneHashService phoneHashService;
    private final SolapiSmsClient solapiSmsClient;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PhoneVerificationService(
        UserRepository userRepository,
        PhoneVerificationCodeRepository phoneVerificationCodeRepository,
        PhoneNumberNormalizer phoneNumberNormalizer,
        PhoneHashService phoneHashService,
        SolapiSmsClient solapiSmsClient,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.phoneVerificationCodeRepository = phoneVerificationCodeRepository;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.phoneHashService = phoneHashService;
        this.solapiSmsClient = solapiSmsClient;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void requestVerification(AppUser user, String phoneNumber) {
        String phoneE164 = phoneNumberNormalizer.toE164(phoneNumber);
        String phoneHash = phoneHashService.hash(phoneE164);
        if (userRepository.existsByVerifiedPhoneHash(phoneHash)) {
            throw new IllegalStateException("이미 가입된 전화번호입니다.");
        }

        String code = "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
        PhoneVerificationCode verificationCode = new PhoneVerificationCode(
            user,
            phoneE164,
            phoneHash,
            passwordEncoder.encode(code),
            Instant.now(clock).plusSeconds(EXPIRES_IN_SECONDS)
        );
        phoneVerificationCodeRepository.save(verificationCode);

        solapiSmsClient.sendVerificationCode(phoneNumberNormalizer.toSmsReceiver(phoneE164), code);
    }

    @Transactional
    public void verify(AppUser user, String phoneNumber, String code) {
        String phoneE164 = phoneNumberNormalizer.toE164(phoneNumber);
        String phoneHash = phoneHashService.hash(phoneE164);
        if (userRepository.existsByVerifiedPhoneHash(phoneHash)) {
            throw new IllegalStateException("이미 가입된 전화번호입니다.");
        }

        PhoneVerificationCode verificationCode = phoneVerificationCodeRepository
            .findFirstByUserIdAndPhoneHashAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId(), phoneHash)
            .orElseThrow(() -> new IllegalStateException("요청된 인증번호를 찾을 수 없습니다."));

        Instant now = Instant.now(clock);
        if (verificationCode.isExpired(now)) {
            throw new IllegalStateException("인증번호가 만료되었습니다.");
        }
        if (verificationCode.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("인증 시도 횟수를 초과했습니다.");
        }

        verificationCode.increaseAttempts();
        if (!passwordEncoder.matches(code.trim(), verificationCode.getCodeHash())) {
            throw new IllegalStateException("인증번호가 올바르지 않습니다.");
        }

        verificationCode.consume(now);
        user.verifyPhone(phoneE164, phoneHash, now);
    }
}
