package it.eng.dcp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsentRecordTest {

    @Test
    @DisplayName("isExpired returns true when expiresAt is before or equal to now")
    void isExpiredReturnsTrueWhenExpired() {
        Instant now = Instant.now();
        ConsentRecord rec = ConsentRecord.Builder.newInstance()
                .holderDid("did:web:example:holder")
                .requested(List.of("read", "write"))
                .granted(List.of("read"))
                .issuedAt(now.minus(1, ChronoUnit.DAYS))
                .expiresAt(now.minus(1, ChronoUnit.SECONDS))
                .build();

        assertTrue(rec.isExpired(now));
    }

    @Test
    @DisplayName("isExpired returns false when expiresAt is after now")
    void isExpiredReturnsFalseWhenNotExpired() {
        Instant now = Instant.now();
        ConsentRecord rec = ConsentRecord.Builder.newInstance()
                .holderDid("did:web:example:holder")
                .requested(List.of("read", "write"))
                .granted(List.of("read"))
                .issuedAt(now.minus(1, ChronoUnit.DAYS))
                .expiresAt(now.plus(1, ChronoUnit.DAYS))
                .build();

        assertFalse(rec.isExpired(now));
    }

    @Test
    @DisplayName("granted must be subset of requested")
    void grantedMustBeSubsetOfRequested() {
        Exception ex = assertThrows(Exception.class, () ->
                ConsentRecord.Builder.newInstance()
                        .holderDid("did:web:example:holder")
                        .requested(List.of("read"))
                        .granted(List.of("write"))
                        .build()
        );

        assertTrue(ex.getMessage().contains("granted must be subset of requested"));
    }
}

