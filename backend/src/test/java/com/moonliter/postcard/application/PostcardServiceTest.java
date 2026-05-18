package com.moonliter.postcard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moonliter.delivery.domain.PostcardDelivery;
import com.moonliter.delivery.repository.PostcardDeliveryRepository;
import com.moonliter.postcard.domain.Postcard;
import com.moonliter.postcard.domain.PostcardStatus;
import com.moonliter.postcard.repository.PostcardRepository;
import com.moonliter.user.domain.AppUser;
import com.moonliter.user.domain.AuthProvider;
import com.moonliter.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostcardServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-05-13T00:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostcardRepository postcardRepository;

    @Mock
    private PostcardDeliveryRepository postcardDeliveryRepository;

    private PostcardService postcardService;

    @BeforeEach
    void setUp() {
        postcardService = new PostcardService(
            userRepository,
            postcardRepository,
            postcardDeliveryRepository,
            FIXED_CLOCK
        );
    }

    @Test
    void createTrimsContentAndSavesPostcard() {
        AppUser author = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(postcardRepository.save(any(Postcard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Postcard postcard = postcardService.create(1L, "  오늘 밤 공기가 좋네요.  ");

        assertThat(postcard.getAuthor()).isEqualTo(author);
        assertThat(postcard.getContent()).isEqualTo("오늘 밤 공기가 좋네요.");
        assertThat(postcard.getStatus()).isEqualTo(PostcardStatus.DELIVERABLE);
    }

    @Test
    void assignTodayReturnsExistingDeliveryForSameDate() {
        AppUser receiver = user(2L);
        PostcardDelivery existing = new PostcardDelivery(
            new Postcard(user(1L), "이미 배정된 엽서"),
            receiver,
            LocalDate.of(2026, 5, 13)
        );
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(postcardDeliveryRepository.findByReceiverIdAndDeliveredDate(
            2L,
            LocalDate.of(2026, 5, 13)
        )).thenReturn(Optional.of(existing));

        PostcardDelivery delivery = postcardService.assignToday(2L);

        assertThat(delivery).isEqualTo(existing);
        verify(postcardRepository, never()).findCandidatesForDelivery(any(), any(), any());
        verify(postcardDeliveryRepository, never()).save(any());
    }

    @Test
    void assignTodayCreatesDeliveryFromCandidate() {
        AppUser receiver = user(2L);
        Postcard candidate = new Postcard(user(1L), "전달할 엽서");
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(postcardDeliveryRepository.findByReceiverIdAndDeliveredDate(
            2L,
            LocalDate.of(2026, 5, 13)
        )).thenReturn(Optional.empty());
        when(postcardRepository.findCandidatesForDelivery(
            eq(2L),
            eq(PostcardStatus.DELIVERABLE),
            any(Pageable.class)
        )).thenReturn(List.of(candidate));
        when(postcardDeliveryRepository.save(any(PostcardDelivery.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PostcardDelivery delivery = postcardService.assignToday(2L);

        assertThat(delivery.getPostcard()).isEqualTo(candidate);
        assertThat(delivery.getReceiver()).isEqualTo(receiver);
        assertThat(delivery.getDeliveredDate()).isEqualTo(LocalDate.of(2026, 5, 13));
    }

    @Test
    void assignTodayFailsWhenThereIsNoCandidate() {
        AppUser receiver = user(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(postcardDeliveryRepository.findByReceiverIdAndDeliveredDate(
            2L,
            LocalDate.of(2026, 5, 13)
        )).thenReturn(Optional.empty());
        when(postcardRepository.findCandidatesForDelivery(
            eq(2L),
            eq(PostcardStatus.DELIVERABLE),
            any(Pageable.class)
        )).thenReturn(List.of());

        assertThatThrownBy(() -> postcardService.assignToday(2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("전달 가능한 엽서가 없습니다.");
    }

    @Test
    void getTodayFailsWhenDeliveryDoesNotExist() {
        AppUser receiver = user(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(postcardDeliveryRepository.findByReceiverIdAndDeliveredDate(
            2L,
            LocalDate.of(2026, 5, 13)
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postcardService.getToday(2L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("오늘 배정된 엽서가 없습니다.");
    }

    @Test
    void assignTodaySearchesWithReceiverIdToExcludeOwnPostcards() {
        AppUser receiver = user(2L);
        Postcard candidate = new Postcard(user(1L), "전달할 엽서");
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(postcardDeliveryRepository.findByReceiverIdAndDeliveredDate(
            2L,
            LocalDate.of(2026, 5, 13)
        )).thenReturn(Optional.empty());
        when(postcardRepository.findCandidatesForDelivery(any(), any(), any()))
            .thenReturn(List.of(candidate));
        when(postcardDeliveryRepository.save(any(PostcardDelivery.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        postcardService.assignToday(2L);

        ArgumentCaptor<Long> receiverId = ArgumentCaptor.forClass(Long.class);
        verify(postcardRepository).findCandidatesForDelivery(
            receiverId.capture(),
            eq(PostcardStatus.DELIVERABLE),
            any(Pageable.class)
        );
        assertThat(receiverId.getValue()).isEqualTo(2L);
    }

    @Test
    void createFailsWhenEmailUserIsNotVerified() {
        AppUser author = unverifiedUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> postcardService.create(1L, "안녕하세요."))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("이메일 인증 후 이용할 수 있습니다.");

        verify(postcardRepository, never()).save(any());
    }

    @Test
    void assignTodayFailsWhenUserIsSuspended() {
        AppUser receiver = user(2L);
        receiver.suspendUntil(Instant.parse("2026-05-14T00:00:00Z"), FIXED_CLOCK.instant());
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));

        assertThatThrownBy(() -> postcardService.assignToday(2L))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("정지된 사용자는 주요 기능을 사용할 수 없습니다.");

        verify(postcardDeliveryRepository, never()).findByReceiverIdAndDeliveredDate(any(), any());
    }

    private AppUser user(Long id) {
        AppUser user = unverifiedUser(id);
        user.verifyEmail(FIXED_CLOCK.instant());
        return user;
    }

    private AppUser unverifiedUser(Long id) {
        AppUser user = new AppUser(
            "user" + id + "@example.com",
            AuthProvider.EMAIL,
            "user-" + id
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
