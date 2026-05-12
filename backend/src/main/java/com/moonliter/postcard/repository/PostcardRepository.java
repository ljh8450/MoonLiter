package com.moonliter.postcard.repository;

import com.moonliter.postcard.domain.Postcard;
import com.moonliter.postcard.domain.PostcardStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostcardRepository extends JpaRepository<Postcard, Long> {

    @Query("""
        select p
        from Postcard p
        where p.status = :status
          and p.author.id <> :receiverId
          and not exists (
              select d.id
              from PostcardDelivery d
              where d.postcard = p
                and d.receiver.id = :receiverId
          )
        order by function('rand')
        """)
    List<Postcard> findCandidatesForDelivery(
        @Param("receiverId") Long receiverId,
        @Param("status") PostcardStatus status,
        Pageable pageable
    );
}
