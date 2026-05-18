package com.moonliter.admin.repository;

import com.moonliter.admin.domain.UserSuspension;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSuspensionRepository extends JpaRepository<UserSuspension, Long> {

    Optional<UserSuspension> findFirstByUserIdAndReleasedAtIsNullOrderByCreatedAtDesc(Long userId);
}
