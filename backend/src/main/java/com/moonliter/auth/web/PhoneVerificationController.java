package com.moonliter.auth.web;

import com.moonliter.auth.application.CurrentUserService;
import com.moonliter.auth.application.PhoneVerificationService;
import com.moonliter.common.response.ApiResponse;
import com.moonliter.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "계정 신뢰성 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/account/phone")
public class PhoneVerificationController {

    private final CurrentUserService currentUserService;
    private final PhoneVerificationService phoneVerificationService;

    public PhoneVerificationController(
        CurrentUserService currentUserService,
        PhoneVerificationService phoneVerificationService
    ) {
        this.currentUserService = currentUserService;
        this.phoneVerificationService = phoneVerificationService;
    }

    @Operation(
        summary = "전화번호 인증번호 요청",
        description = "인증된 사용자에게 SOLAPI SMS로 전화번호 인증번호를 발송한다."
    )
    @PostMapping("/verification")
    public ApiResponse<Void> requestVerification(
        @Parameter(hidden = true)
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PhoneVerificationRequest request
    ) {
        AppUser currentUser = currentUserService.requireCurrentUser(jwt);
        phoneVerificationService.requestVerification(currentUser, request.phoneNumber());
        return ApiResponse.ok(null);
    }

    @Operation(
        summary = "전화번호 인증번호 확인",
        description = "SOLAPI SMS로 받은 인증번호를 확인하고 사용자 계정에 인증된 전화번호를 연결한다."
    )
    @PostMapping("/verification/confirm")
    public ApiResponse<Void> confirmVerification(
        @Parameter(hidden = true)
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PhoneVerificationConfirmRequest request
    ) {
        AppUser currentUser = currentUserService.requireCurrentUser(jwt);
        phoneVerificationService.verify(currentUser, request.phoneNumber(), request.code());
        return ApiResponse.ok(null);
    }

    public record PhoneVerificationRequest(
        @NotBlank(message = "전화번호를 입력해주세요.")
        String phoneNumber
    ) {
    }

    public record PhoneVerificationConfirmRequest(
        @NotBlank(message = "전화번호를 입력해주세요.")
        String phoneNumber,

        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자입니다.")
        String code
    ) {
    }
}
