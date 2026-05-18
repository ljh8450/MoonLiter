package com.moonliter.auth.web;

import com.moonliter.auth.application.CurrentUserService;
import com.moonliter.common.response.ApiResponse;
import com.moonliter.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "계정 신뢰성 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/account")
public class CurrentAccountController {

    private final CurrentUserService currentUserService;

    public CurrentAccountController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Operation(
        summary = "현재 계정 상태 조회",
        description = "현재 사용자의 이메일/전화번호 인증 상태를 조회한다."
    )
    @GetMapping("/me")
    public ApiResponse<CurrentAccountResponse> getCurrentAccount(
        @Parameter(hidden = true)
        @AuthenticationPrincipal Jwt jwt
    ) {
        AppUser currentUser = currentUserService.requireCurrentUser(jwt);
        return ApiResponse.ok(CurrentAccountResponse.from(currentUser));
    }

    public record CurrentAccountResponse(
        Long userId,
        String email,
        boolean emailVerified,
        Instant emailVerifiedAt,
        boolean phoneVerified,
        Instant phoneVerifiedAt
    ) {

        static CurrentAccountResponse from(AppUser user) {
            return new CurrentAccountResponse(
                user.getId(),
                user.getEmail(),
                user.canUseEmailVerifiedFeatures(),
                user.getEmailVerifiedAt(),
                user.getPhoneVerifiedAt() != null,
                user.getPhoneVerifiedAt()
            );
        }
    }
}
