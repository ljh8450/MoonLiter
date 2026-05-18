package com.moonliter.auth.application;

import com.moonliter.user.domain.AppUser;
import com.moonliter.user.domain.AuthProvider;
import com.moonliter.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private static final String SUPABASE_APP_METADATA = "app_metadata";
    private static final String PROVIDER = "provider";
    private static final String EMAIL_CONFIRMED_AT = "email_confirmed_at";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AppUser requireCurrentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("인증된 사용자 정보를 확인할 수 없습니다.");
        }

        AuthProvider authProvider = resolveAuthProvider(jwt);
        String providerUserId = jwt.getSubject();

        return userRepository.findByAuthProviderAndProviderUserId(authProvider, providerUserId)
            .orElseGet(() -> createUser(jwt, authProvider, providerUserId));
    }

    @Transactional(readOnly = true)
    public AppUser requireExistingCurrentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("인증된 사용자 정보를 확인할 수 없습니다.");
        }

        AuthProvider authProvider = resolveAuthProvider(jwt);
        return userRepository.findByAuthProviderAndProviderUserId(authProvider, jwt.getSubject())
            .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
    }

    private AppUser createUser(Jwt jwt, AuthProvider authProvider, String providerUserId) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("JWT에 이메일 정보가 없습니다.");
        }

        AppUser user = new AppUser(email, authProvider, providerUserId);
        if (authProvider == AuthProvider.EMAIL) {
            Instant emailConfirmedAt = parseInstantClaim(jwt, EMAIL_CONFIRMED_AT);
            if (emailConfirmedAt != null) {
                user.verifyEmail(emailConfirmedAt);
            }
        }
        return userRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private AuthProvider resolveAuthProvider(Jwt jwt) {
        Object appMetadata = jwt.getClaims().get(SUPABASE_APP_METADATA);
        if (!(appMetadata instanceof Map<?, ?> metadata)) {
            return AuthProvider.EMAIL;
        }

        Object provider = metadata.get(PROVIDER);
        if (!(provider instanceof String providerName)) {
            return AuthProvider.EMAIL;
        }

        return switch (providerName.toLowerCase(Locale.ROOT)) {
            case "google" -> AuthProvider.GOOGLE;
            case "kakao" -> AuthProvider.KAKAO;
            default -> AuthProvider.EMAIL;
        };
    }

    private Instant parseInstantClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Instant.parse(text);
        }
        return null;
    }
}
