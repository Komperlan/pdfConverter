package com.docconverter.application.port.out;

import com.docconverter.domain.model.ConversionJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversionJobRepository {

    ConversionJob save(ConversionJob job);

    Optional<ConversionJob> findById(UUID id);

    Optional<ConversionJob> findByIdForUpdate(UUID id);

    List<ConversionJob> claimNextForProcessing(int limit, Instant startedAt);

    List<UUID> findStalledProcessingIds(Instant staleBefore, int limit);

    List<UUID> findCleanupCandidateIds(Instant expiresAt, int limit);
}
