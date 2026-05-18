package com.moonliter.admin.application;

import com.moonliter.admin.domain.UserSuspension;
import com.moonliter.admin.repository.UserSuspensionRepository;
import com.moonliter.notification.domain.AppNotification;
import com.moonliter.notification.domain.NotificationType;
import com.moonliter.notification.repository.AppNotificationRepository;
import com.moonliter.user.domain.AppUser;
import com.moonliter.user.domain.UserRole;
import com.moonliter.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSuspensionService {

    private final UserRepository userRepository;
    private final UserSuspensionRepository userSuspensionRepository;
    private final AppNotificationRepository appNotificationRepository;
    private final SuspensionPolicy suspensionPolicy;
    private final Clock clock;

    public UserSuspensionService(
        UserRepository userRepository,
        UserSuspensionRepository userSuspensionRepository,
        AppNotificationRepository appNotificationRepository,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.userSuspensionRepository = userSuspensionRepository;
        this.appNotificationRepository = appNotificationRepository;
        this.suspensionPolicy = new SuspensionPolicy();
        this.clock = clock;
    }

    @Transactional
    public UserSuspension suspend(Long adminId, Long userId, String reason) {
        AppUser admin = findAdmin(adminId);
        AppUser user = findUser(userId);
        if (admin.getId().equals(user.getId())) {
            throw new IllegalStateException("관리자는 자기 자신을 정지할 수 없습니다.");
        }

        Instant now = Instant.now(clock);
        int nextCount = user.increaseSuspensionCount(now);
        SuspensionDecision decision = suspensionPolicy.decide(nextCount, now);

        if (decision.permanent()) {
            user.suspendPermanently(now);
        } else {
            user.suspendUntil(decision.endsAt(), now);
        }

        UserSuspension suspension = userSuspensionRepository.save(new UserSuspension(
            user,
            admin,
            reason.trim(),
            decision.suspensionCount(),
            decision.startsAt(),
            decision.endsAt(),
            decision.permanent()
        ));
        appNotificationRepository.save(createSuspensionNotification(user, suspension));

        return suspension;
    }

    @Transactional
    public void unsuspend(Long adminId, Long userId, String reason) {
        findAdmin(adminId);
        AppUser user = findUser(userId);
        Instant now = Instant.now(clock);

        user.clearSuspension(now);
        userSuspensionRepository.findFirstByUserIdAndReleasedAtIsNullOrderByCreatedAtDesc(userId)
            .ifPresent(suspension -> suspension.release(reason.trim(), now));
    }

    @Transactional(readOnly = true)
    public UserIntegrity getUserIntegrity(Long adminId, Long userId) {
        findAdmin(adminId);
        AppUser user = findUser(userId);
        Instant now = Instant.now(clock);

        return new UserIntegrity(
            user.getId(),
            user.getEmail(),
            user.canUseEmailVerifiedFeatures(),
            user.getEmailVerifiedAt(),
            user.getPhoneVerifiedAt() != null,
            user.getPhoneVerifiedAt(),
            user.getSuspensionCount(),
            user.isSuspendedAt(now),
            user.getSuspendedUntil(),
            user.isPermanentlySuspended()
        );
    }

    private AppNotification createSuspensionNotification(AppUser user, UserSuspension suspension) {
        String body = suspension.isPermanent()
            ? "정지 사유: %s\n정지 기간: 영구 정지".formatted(suspension.getReason())
            : "정지 사유: %s\n정지 해제 예정일: %s".formatted(
                suspension.getReason(),
                suspension.getEndsAt()
            );

        return new AppNotification(
            user,
            NotificationType.USER_SUSPENDED,
            "계정 이용이 제한되었습니다.",
            body
        );
    }

    private AppUser findAdmin(Long adminId) {
        AppUser admin = findUser(adminId);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }
        return admin;
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
    }

    public record UserIntegrity(
        Long userId,
        String email,
        boolean emailVerifiedFeatureAllowed,
        Instant emailVerifiedAt,
        boolean phoneVerified,
        Instant phoneVerifiedAt,
        int suspensionCount,
        boolean suspended,
        Instant suspendedUntil,
        boolean permanentlySuspended
    ) {
    }
}
