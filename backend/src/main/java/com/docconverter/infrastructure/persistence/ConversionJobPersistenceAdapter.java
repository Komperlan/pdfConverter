package com.docconverter.infrastructure.persistence;

import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.domain.model.ConversionJob;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class ConversionJobPersistenceAdapter implements ConversionJobRepository {

    private final ConversionJobJpaRepository jpaRepository;

    @Override
    public ConversionJob save(ConversionJob job) {
        return jpaRepository.save(job);
    }

    @Override
    public Optional<ConversionJob> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ConversionJob> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id);
    }

    @Override
    @Transactional
    public List<ConversionJob> claimNextForProcessing(int limit, Instant startedAt) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Objects.requireNonNull(startedAt, "startedAt must not be null");

        List<ConversionJob> jobs = jpaRepository.findNextJobsForProcessing(limit, startedAt);
        jobs.forEach(job -> job.startProcessing(startedAt));
        jpaRepository.flush();
        return jobs;
    }

    @Override
    public List<UUID> findStalledProcessingIds(Instant staleBefore, int limit) {
        Objects.requireNonNull(staleBefore, "staleBefore must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jpaRepository.findStalledProcessingIds(staleBefore, limit);
    }

    @Override
    public List<UUID> findCleanupCandidateIds(Instant expiresAt, int limit) {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jpaRepository.findCleanupCandidateIds(expiresAt, limit);
    }

}
