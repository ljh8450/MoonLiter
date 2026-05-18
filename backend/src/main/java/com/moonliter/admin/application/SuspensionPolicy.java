package com.moonliter.admin.application;

import java.time.Instant;
import java.time.ZoneOffset;

public class SuspensionPolicy {

    public SuspensionDecision decide(int suspensionCount, Instant startsAt) {
        if (suspensionCount <= 0) {
            throw new IllegalArgumentException("정지 횟수는 1 이상이어야 합니다.");
        }
        if (suspensionCount >= 5) {
            return new SuspensionDecision(suspensionCount, startsAt, null, true);
        }

        Instant endsAt = switch (suspensionCount) {
            case 1 -> startsAt.plusSeconds(24 * 60 * 60);
            case 2 -> startsAt.plusSeconds(7 * 24 * 60 * 60);
            case 3 -> startsAt.plusSeconds(30 * 24 * 60 * 60);
            case 4 -> startsAt.atOffset(ZoneOffset.UTC).plusYears(1).toInstant();
            default -> throw new IllegalArgumentException("정지 횟수는 1 이상이어야 합니다.");
        };

        return new SuspensionDecision(
            suspensionCount,
            startsAt,
            endsAt,
            false
        );
    }
}
