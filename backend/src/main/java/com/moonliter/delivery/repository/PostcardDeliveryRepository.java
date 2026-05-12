package com.moonliter.delivery.repository;

import com.moonliter.delivery.domain.PostcardDelivery;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostcardDeliveryRepository extends JpaRepository<PostcardDelivery, Long> {

    @Query("""
        select d
        from PostcardDelivery d
        join fetch d.postcard
        where d.receiver.id = :receiverId
          and d.deliveredDate = :deliveredDate
        """)
    Optional<PostcardDelivery> findByReceiverIdAndDeliveredDate(
        @Param("receiverId") Long receiverId,
        @Param("deliveredDate") LocalDate deliveredDate
    );
}
