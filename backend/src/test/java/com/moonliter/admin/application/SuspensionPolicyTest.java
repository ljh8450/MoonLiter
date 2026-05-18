package com.moonliter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SuspensionPolicyTest {

    private final SuspensionPolicy suspensionPolicy = new SuspensionPolicy();
    private final Instant startsAt = Instant.parse("2026-05-18T00:00:00Z");

    @Test
    void decideReturnsEscalatingPeriods() {
        assertThat(suspensionPolicy.decide(1, startsAt).endsAt())
            .isEqualTo(Instant.parse("2026-05-19T00:00:00Z"));
        assertThat(suspensionPolicy.decide(2, startsAt).endsAt())
            .isEqualTo(Instant.parse("2026-05-25T00:00:00Z"));
        assertThat(suspensionPolicy.decide(3, startsAt).endsAt())
            .isEqualTo(Instant.parse("2026-06-17T00:00:00Z"));
        assertThat(suspensionPolicy.decide(4, startsAt).endsAt())
            .isEqualTo(Instant.parse("2027-05-18T00:00:00Z"));
    }

    @Test
    void decideReturnsPermanentFromFifthSuspension() {
        SuspensionDecision decision = suspensionPolicy.decide(5, startsAt);

        assertThat(decision.permanent()).isTrue();
        assertThat(decision.endsAt()).isNull();
    }
}
