package com.moonliter.admin.application;

import java.time.Instant;

public record SuspensionDecision(
    int suspensionCount,
    Instant startsAt,
    Instant endsAt,
    boolean permanent
) {
}
