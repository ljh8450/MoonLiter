package com.moonliter.auth.repository;

import com.moonliter.auth.domain.PhoneVerificationCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneVerificationCodeRepository extends JpaRepository<PhoneVerificationCode, Long> {

    Optional<PhoneVerificationCode> findFirstByUserIdAndPhoneHashAndConsumedAtIsNullOrderByCreatedAtDesc(
        Long userId,
        String phoneHash
    );
}
