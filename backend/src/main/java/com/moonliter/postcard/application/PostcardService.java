package com.moonliter.postcard.application;

import com.moonliter.delivery.domain.PostcardDelivery;
import com.moonliter.delivery.repository.PostcardDeliveryRepository;
import com.moonliter.postcard.domain.Postcard;
import com.moonliter.postcard.domain.PostcardStatus;
import com.moonliter.postcard.repository.PostcardRepository;
import com.moonliter.user.domain.AppUser;
import com.moonliter.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostcardService {

    private final UserRepository userRepository;
    private final PostcardRepository postcardRepository;
    private final PostcardDeliveryRepository postcardDeliveryRepository;
    private final Clock clock;

    public PostcardService(
        UserRepository userRepository,
        PostcardRepository postcardRepository,
        PostcardDeliveryRepository postcardDeliveryRepository,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.postcardRepository = postcardRepository;
        this.postcardDeliveryRepository = postcardDeliveryRepository;
        this.clock = clock;
    }

    @Transactional
    public Postcard create(Long authorId, String content) {
        AppUser author = findUser(authorId);
        return postcardRepository.save(new Postcard(author, content.trim()));
    }

    @Transactional
    public PostcardDelivery assignToday(Long receiverId) {
        AppUser receiver = findUser(receiverId);
        LocalDate today = LocalDate.now(clock);

        return postcardDeliveryRepository.findByReceiverIdAndDeliveredDate(receiverId, today)
            .orElseGet(() -> createTodayDelivery(receiver));
    }

    @Transactional(readOnly = true)
    public PostcardDelivery getToday(Long receiverId) {
        findUser(receiverId);
        LocalDate today = LocalDate.now(clock);

        return postcardDeliveryRepository.findByReceiverIdAndDeliveredDate(receiverId, today)
            .orElseThrow(() -> new EntityNotFoundException("오늘 배정된 엽서가 없습니다."));
    }

    private PostcardDelivery createTodayDelivery(AppUser receiver) {
        Postcard postcard = postcardRepository.findCandidatesForDelivery(
                receiver.getId(),
                PostcardStatus.DELIVERABLE,
                PageRequest.of(0, 1)
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("전달 가능한 엽서가 없습니다."));

        return postcardDeliveryRepository.save(
            new PostcardDelivery(postcard, receiver, LocalDate.now(clock))
        );
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
