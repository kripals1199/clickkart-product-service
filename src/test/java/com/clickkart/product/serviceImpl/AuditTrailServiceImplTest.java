// src/test/java/com/clickkart/product/serviceImpl/AuditTrailServiceImplTest.java
package com.clickkart.product.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.product.entity.AuditChainHeadEntity;
import com.clickkart.product.entity.AuditLogEntryEntity;
import com.clickkart.product.enums.AuditOutcome;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.feign.AuditLogServiceClient;
import com.clickkart.product.repository.AuditChainHeadRepository;
import com.clickkart.product.repository.AuditLogEntryRepository;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.web.RequestMetadata;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The audit chain's correctness properties.
 *
 * <p>Unlike most suites here, the hash logic is genuinely unit-testable end to end - it is pure
 * computation over the entry's own fields, so these tests prove the real thing rather than a mocked
 * approximation. What they cannot prove is the pessimistic lock actually serialising concurrent
 * writers, which needs a real database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditTrailServiceImplTest {

    private static final String CORR = "corr-1";
    private static final String ACTOR = "USR-alice";

    @Mock private AuditLogEntryRepository auditLogEntryRepository;
    @Mock private AuditChainHeadRepository auditChainHeadRepository;
    @Mock private AuditLogServiceClient auditLogServiceClient;

    private AuditTrailServiceImpl service;
    private AuditChainHeadEntity head;

    @BeforeEach
    void setUp() {
        service = new AuditTrailServiceImpl(auditLogEntryRepository, auditChainHeadRepository, auditLogServiceClient);
        head = new AuditChainHeadEntity(AuditTrailService.GENESIS_HASH);
        when(auditChainHeadRepository.lockForUpdate(AuditChainHeadEntity.SINGLETON_ID)).thenReturn(Optional.of(head));
    }

    @Nested
    @DisplayName("recording")
    class Recording {

        @Test
        @DisplayName("the first entry links to the genesis hash")
        void firstEntryLinksToGenesis() {
            service.record(CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, metadata(), "created");

            assertThat(captured().getPreviousEntryHash()).isEqualTo(AuditTrailService.GENESIS_HASH);
        }

        @Test
        @DisplayName("the head advances to the new entry's hash, and counts it")
        void headAdvances() {
            service.record(CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, metadata(), "created");

            assertThat(head.getLastEntryHash()).isEqualTo(captured().getEntryHash());
            assertThat(head.getEntryCount()).isEqualTo(1);
            verify(auditChainHeadRepository).save(head);
        }

        @Test
        @DisplayName("a missing chain head is a hard failure, not a silently unchained entry")
        void missingHeadFails() {
            when(auditChainHeadRepository.lockForUpdate(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.record(CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, metadata(), "x"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chain head");
        }

        @Test
        @DisplayName("the default overload records a success")
        void defaultOutcomeIsSuccess() {
            service.record(CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, metadata(), "created");

            assertThat(captured().getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        }

        @Test
        @DisplayName("a failure outcome is recorded as such")
        void failureIsRecorded() {
            service.record(CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, AuditOutcome.FAILURE, metadata(), "denied");

            assertThat(captured().getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        }

        /**
         * The whole point of keeping a local trail: the central service being unreachable must not
         * cost the local entry, and must not fail the customer's request either.
         */
        @Test
        @DisplayName("a central dispatch failure leaves the local entry standing")
        void centralFailureDoesNotLoseTheLocalEntry() {
            org.mockito.Mockito.doThrow(new IllegalStateException("audit-log-service down"))
                    .when(auditLogServiceClient)
                    .logEvent(anyString(), any());

            assertThatCode(() -> service.record(CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, metadata(), "created"))
                    .doesNotThrowAnyException();

            verify(auditLogEntryRepository).save(any(AuditLogEntryEntity.class));
            assertThat(head.getEntryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("the event is also dispatched centrally when the service is up")
        void dispatchesCentrally() {
            service.record(CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, metadata(), "created");

            verify(auditLogServiceClient).logEvent(anyString(), any());
        }
    }

    @Nested
    @DisplayName("hashing")
    class Hashing {

        /**
         * The bug this pins cost a live debugging session: Instant.now() carries nanoseconds, the
         * Postgres column holds microseconds, and hashing the untruncated value made every entry
         * fail its own integrity check the moment it was read back.
         */
        @Test
        @DisplayName("the timestamp is truncated to microseconds, so the hashed value survives a round trip")
        void timestampIsTruncatedToMicros() {
            Instant withNanos = Instant.parse("2026-01-01T00:00:00Z").plusNanos(123_456_789L);

            AuditLogEntryEntity entry = AuditLogEntryEntity.create(
                    withNanos, CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, AuditOutcome.SUCCESS,
                    "203.0.113.1", "junit", "d", AuditTrailService.GENESIS_HASH);

            assertThat(entry.getOccurredAt()).isEqualTo(withNanos.truncatedTo(ChronoUnit.MICROS));
            // The stored value is what was hashed, which is what makes this verify at all.
            assertThat(entry.recomputeHash()).isEqualTo(entry.getEntryHash());
        }

        @Test
        @DisplayName("a hash is 64 hex characters")
        void hashShape() {
            assertThat(entryWith("d").getEntryHash()).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("changing any field changes the hash")
        void hashCoversTheFields() {
            assertThat(entryWith("one").getEntryHash()).isNotEqualTo(entryWith("two").getEntryHash());
        }

        @Test
        @DisplayName("the same inputs always produce the same hash")
        void hashIsDeterministic() {
            Instant at = Instant.parse("2026-01-01T00:00:00Z");
            assertThat(entryAt(at, "d").getEntryHash()).isEqualTo(entryAt(at, "d").getEntryHash());
        }
    }

    @Nested
    @DisplayName("integrity verification")
    class Verification {

        @Test
        @DisplayName("an empty chain is intact")
        void emptyChainIsIntact() {
            when(auditLogEntryRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

            assertThat(service.verifyChainIntegrity().intact()).isTrue();
        }

        @Test
        @DisplayName("a correctly linked chain verifies")
        void goodChainVerifies() {
            when(auditLogEntryRepository.findAllByOrderByIdAsc()).thenReturn(chainOf("a", "b", "c"));

            var report = service.verifyChainIntegrity();

            assertThat(report.intact()).isTrue();
            assertThat(report.entriesChecked()).isEqualTo(3);
            assertThat(report.brokenAtEntryId()).isNull();
        }

        /** Detects the row whose stored hash no longer matches its own contents. */
        @Test
        @DisplayName("an edited entry is caught, and named")
        void detectsAnEditedEntry() {
            List<AuditLogEntryEntity> chain = chainOf("a", "b", "c");
            setField(chain.get(1), "details", "TAMPERED");
            setField(chain.get(1), "id", 42L);
            when(auditLogEntryRepository.findAllByOrderByIdAsc()).thenReturn(chain);

            var report = service.verifyChainIntegrity();

            assertThat(report.intact()).isFalse();
            assertThat(report.brokenAtEntryId()).isEqualTo(42L);
            assertThat(report.reason()).contains("recomputed hash");
        }

        /** Detects a deletion, which breaks a link rather than a hash - a different failure. */
        @Test
        @DisplayName("a removed entry is caught as a broken link, not a bad hash")
        void detectsARemovedEntry() {
            List<AuditLogEntryEntity> chain = chainOf("a", "b", "c");
            setField(chain.get(2), "id", 99L);
            List<AuditLogEntryEntity> withHole = List.of(chain.get(0), chain.get(2));
            when(auditLogEntryRepository.findAllByOrderByIdAsc()).thenReturn(withHole);

            var report = service.verifyChainIntegrity();

            assertThat(report.intact()).isFalse();
            assertThat(report.brokenAtEntryId()).isEqualTo(99L);
            assertThat(report.reason()).contains("chain link broken");
        }
    }

    // ------------------------------------------------------------------ fixtures

    private RequestMetadata metadata() {
        return new RequestMetadata("203.0.113.1", "junit");
    }

    private AuditLogEntryEntity captured() {
        ArgumentCaptor<AuditLogEntryEntity> captor = ArgumentCaptor.captor();
        verify(auditLogEntryRepository).save(captor.capture());
        return captor.getValue();
    }

    private AuditLogEntryEntity entryWith(String details) {
        return entryAt(Instant.parse("2026-01-01T00:00:00Z"), details);
    }

    private AuditLogEntryEntity entryAt(Instant at, String details) {
        return AuditLogEntryEntity.create(
                at, CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, AuditOutcome.SUCCESS,
                "203.0.113.1", "junit", details, AuditTrailService.GENESIS_HASH);
    }

    /** Builds a genuinely linked chain, each entry pointing at the real hash of the one before. */
    private List<AuditLogEntryEntity> chainOf(String... detailValues) {
        java.util.List<AuditLogEntryEntity> entries = new java.util.ArrayList<>();
        String previous = AuditTrailService.GENESIS_HASH;
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        for (String d : detailValues) {
            AuditLogEntryEntity e = AuditLogEntryEntity.create(
                    at, CORR, ACTOR, ProductAuditAction.PRODUCT_CREATED, AuditOutcome.SUCCESS,
                    "203.0.113.1", "junit", d, previous);
            entries.add(e);
            previous = e.getEntryHash();
            at = at.plusSeconds(1);
        }
        return entries;
    }

    /** The entity is deliberately immutable, so tampering in a test needs reflection - as it should. */
    private void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
