package com.moonliter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moonliter.admin.domain.UserSuspension;
import com.moonliter.admin.repository.UserSuspensionRepository;
import com.moonliter.notification.domain.AppNotification;
import com.moonliter.notification.repository.AppNotificationRepository;
import com.moonliter.user.domain.AppUser;
import com.moonliter.user.domain.AuthProvider;
import com.moonliter.user.domain.UserRole;
import com.moonliter.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserSuspensionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-05-18T00:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSuspensionRepository userSuspensionRepository;

    @Mock
    private AppNotificationRepository appNotificationRepository;

    private UserSuspensionService userSuspensionService;

    @BeforeEach
    void setUp() {
        userSuspensionService = new UserSuspensionService(
            userRepository,
            userSuspensionRepository,
            appNotificationRepository,
            FIXED_CLOCK
        );
    }

    @Test
    void suspendAppliesNextSuspensionPeriodAndCreatesNotification() {
        AppUser admin = user(1L);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        AppUser target = user(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userSuspensionRepository.save(any(UserSuspension.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserSuspension suspension = userSuspensionService.suspend(1L, 2L, "  반복 신고  ");

        assertThat(target.getSuspensionCount()).isEqualTo(1);
        assertThat(target.isSuspendedAt(FIXED_CLOCK.instant())).isTrue();
        assertThat(target.getSuspendedUntil()).isEqualTo(Instant.parse("2026-05-19T00:00:00Z"));
        assertThat(suspension.getReason()).isEqualTo("반복 신고");
        assertThat(suspension.getSuspensionCount()).isEqualTo(1);

        ArgumentCaptor<AppNotification> notification = ArgumentCaptor.forClass(AppNotification.class);
        verify(appNotificationRepository).save(notification.capture());
        assertThat(notification.getValue().getUser()).isEqualTo(target);
        assertThat(notification.getValue().getTitle()).isEqualTo("계정 이용이 제한되었습니다.");
    }

    @Test
    void suspendRequiresAdminRole() {
        AppUser notAdmin = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(notAdmin));

        assertThatThrownBy(() -> userSuspensionService.suspend(1L, 2L, "사유"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("관리자 권한이 필요합니다.");
    }

    @Test
    void fifthSuspensionBecomesPermanent() {
        AppUser admin = user(1L);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        AppUser target = user(2L);
        ReflectionTestUtils.setField(target, "suspensionCount", 4);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userSuspensionRepository.save(any(UserSuspension.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserSuspension suspension = userSuspensionService.suspend(1L, 2L, "반복 위반");

        assertThat(target.getSuspensionCount()).isEqualTo(5);
        assertThat(target.isPermanentlySuspended()).isTrue();
        assertThat(target.getSuspendedUntil()).isNull();
        assertThat(suspension.isPermanent()).isTrue();
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser(
            "user" + id + "@example.com",
            AuthProvider.EMAIL,
            "user-" + id
        );
        user.verifyEmail(FIXED_CLOCK.instant());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
