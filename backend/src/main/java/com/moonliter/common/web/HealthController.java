package com.moonliter.common.web;

import com.moonliter.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서비스 상태 확인 API")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Operation(summary = "서비스 상태 확인", description = "백엔드 API 서버가 응답 가능한 상태인지 확인한다.")
    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP", Instant.now()));
    }

    public record HealthResponse(
        String status,
        Instant checkedAt
    ) {
    }
}
