package com.moonliter.user.repository;

import com.moonliter.user.domain.AppUser;
import com.moonliter.user.domain.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByAuthProviderAndProviderUserId(
        AuthProvider authProvider,
        String providerUserId
    );

    boolean existsByVerifiedPhoneHash(String verifiedPhoneHash);
}
