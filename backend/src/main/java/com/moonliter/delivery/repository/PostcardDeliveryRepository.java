package com.moonliter.delivery.repository;

import com.moonliter.delivery.domain.PostcardDelivery;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostcardDeliveryRepository extends JpaRepository<PostcardDelivery, Long> {

    Optional<PostcardDelivery> findByReceiverIdAndDeliveredDate(
        Long receiverId,
        LocalDate deliveredDate
    );
}
