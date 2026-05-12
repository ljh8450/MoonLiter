package com.moonliter.postcard.web;

import com.moonliter.common.response.ApiResponse;
import com.moonliter.delivery.domain.PostcardDelivery;
import com.moonliter.postcard.application.PostcardService;
import com.moonliter.postcard.domain.Postcard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Postcards", description = "MVP 엽서 작성과 오늘의 엽서 수신 API")
@RestController
@RequestMapping("/api/v1/postcards")
public class PostcardController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PostcardService postcardService;

    public PostcardController(PostcardService postcardService) {
        this.postcardService = postcardService;
    }

    @Operation(
        summary = "엽서 작성",
        description = "개발용 임시 인증 헤더 X-User-Id의 사용자가 텍스트 엽서를 작성한다."
    )
    @PostMapping
    public ApiResponse<PostcardResponse> create(
        @Parameter(description = "개발용 임시 사용자 ID", required = true)
        @RequestHeader(USER_ID_HEADER) Long userId,
        @Valid @RequestBody CreatePostcardRequest request
    ) {
        return ApiResponse.ok(PostcardResponse.from(postcardService.create(userId, request.content())));
    }

    @Operation(
        summary = "오늘의 엽서 배정",
        description = "개발용 임시 인증 헤더 X-User-Id의 사용자에게 오늘 받을 엽서를 하나 배정한다. 같은 날짜에는 기존 배정을 재사용한다."
    )
    @PostMapping("/today/delivery")
    public ApiResponse<TodayPostcardResponse> assignToday(
        @Parameter(description = "개발용 임시 사용자 ID", required = true)
        @RequestHeader(USER_ID_HEADER) Long userId
    ) {
        return ApiResponse.ok(TodayPostcardResponse.from(postcardService.assignToday(userId)));
    }

    @Operation(
        summary = "오늘의 엽서 조회",
        description = "개발용 임시 인증 헤더 X-User-Id의 사용자에게 오늘 배정된 엽서를 조회한다. 작성자 식별 정보는 응답하지 않는다."
    )
    @GetMapping("/today")
    public ApiResponse<TodayPostcardResponse> getToday(
        @Parameter(description = "개발용 임시 사용자 ID", required = true)
        @RequestHeader(USER_ID_HEADER) Long userId
    ) {
        return ApiResponse.ok(TodayPostcardResponse.from(postcardService.getToday(userId)));
    }

    public record CreatePostcardRequest(
        @NotBlank(message = "엽서 내용을 입력해주세요.")
        @Size(max = 500, message = "엽서는 500자 이내로 적어주세요.")
        String content
    ) {
    }

    public record PostcardResponse(
        Long id,
        String content,
        String status,
        Instant createdAt
    ) {

        static PostcardResponse from(Postcard postcard) {
            return new PostcardResponse(
                postcard.getId(),
                postcard.getContent(),
                postcard.getStatus().name(),
                postcard.getCreatedAt()
            );
        }
    }

    public record TodayPostcardResponse(
        Long deliveryId,
        Long postcardId,
        String content,
        LocalDate deliveredDate,
        Instant deliveredAt
    ) {

        static TodayPostcardResponse from(PostcardDelivery delivery) {
            Postcard postcard = delivery.getPostcard();
            return new TodayPostcardResponse(
                delivery.getId(),
                postcard.getId(),
                postcard.getContent(),
                delivery.getDeliveredDate(),
                delivery.getCreatedAt()
            );
        }
    }
}
