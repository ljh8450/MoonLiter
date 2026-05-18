package com.moonliter.admin.web;

import com.moonliter.auth.application.CurrentUserService;
import com.moonliter.admin.application.UserSuspensionService;
import com.moonliter.admin.application.UserSuspensionService.UserIntegrity;
import com.moonliter.admin.domain.UserSuspension;
import com.moonliter.common.response.ApiResponse;
import com.moonliter.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Users", description = "관리자 사용자 신뢰성 및 정지 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserSuspensionService userSuspensionService;
    private final CurrentUserService currentUserService;

    public AdminUserController(
        UserSuspensionService userSuspensionService,
        CurrentUserService currentUserService
    ) {
        this.userSuspensionService = userSuspensionService;
        this.currentUserService = currentUserService;
    }

    @Operation(
        summary = "사용자 신뢰성 상태 조회",
        description = "관리자가 사용자별 이메일/전화번호 인증 상태와 현재 정지 상태를 조회한다."
    )
    @GetMapping("/{userId}/integrity")
    public ApiResponse<UserIntegrity> getIntegrity(
        @Parameter(hidden = true)
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long userId
    ) {
        AppUser admin = currentUserService.requireCurrentUser(jwt);
        return ApiResponse.ok(userSuspensionService.getUserIntegrity(admin.getId(), userId));
    }

    @Operation(
        summary = "사용자 정지",
        description = "관리자가 사용자를 정지한다. 누적 정지 횟수에 따라 1일, 7일, 30일, 1년, 영구 정지가 자동 적용되고 인앱 알림이 생성된다."
    )
    @PostMapping("/{userId}/suspension")
    public ApiResponse<SuspensionResponse> suspend(
        @Parameter(hidden = true)
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long userId,
        @Valid @RequestBody SuspendUserRequest request
    ) {
        AppUser admin = currentUserService.requireCurrentUser(jwt);
        return ApiResponse.ok(SuspensionResponse.from(
            userSuspensionService.suspend(admin.getId(), userId, request.reason())
        ));
    }

    @Operation(
        summary = "사용자 정지 해제",
        description = "관리자가 사용자의 현재 정지를 수동 해제한다. 누적 정지 횟수는 보존된다."
    )
    @DeleteMapping("/{userId}/suspension")
    public ApiResponse<Void> unsuspend(
        @Parameter(hidden = true)
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long userId,
        @Valid @RequestBody UnsuspendUserRequest request
    ) {
        AppUser admin = currentUserService.requireCurrentUser(jwt);
        userSuspensionService.unsuspend(admin.getId(), userId, request.reason());
        return ApiResponse.ok(null);
    }

    public record SuspendUserRequest(
        @NotBlank(message = "정지 사유를 입력해주세요.")
        @Size(max = 500, message = "정지 사유는 500자 이내로 입력해주세요.")
        String reason
    ) {
    }

    public record UnsuspendUserRequest(
        @NotBlank(message = "해제 사유를 입력해주세요.")
        @Size(max = 500, message = "해제 사유는 500자 이내로 입력해주세요.")
        String reason
    ) {
    }

    public record SuspensionResponse(
        Long id,
        Long userId,
        String reason,
        int suspensionCount,
        Instant startsAt,
        Instant endsAt,
        boolean permanent
    ) {

        static SuspensionResponse from(UserSuspension suspension) {
            return new SuspensionResponse(
                suspension.getId(),
                suspension.getUser().getId(),
                suspension.getReason(),
                suspension.getSuspensionCount(),
                suspension.getStartsAt(),
                suspension.getEndsAt(),
                suspension.isPermanent()
            );
        }
    }
}
