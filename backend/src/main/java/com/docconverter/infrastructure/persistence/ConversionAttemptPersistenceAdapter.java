package com.docconverter.infrastructure.persistence;

import com.docconverter.application.port.out.ConversionAttemptRepository;
import com.docconverter.domain.model.ConversionAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ConversionAttemptPersistenceAdapter implements ConversionAttemptRepository {

    private final ConversionAttemptJpaRepository jpaRepository;

    @Override
    public ConversionAttempt save(ConversionAttempt attempt) {
        return jpaRepository.save(attempt);
    }

    @Override
    public Optional<ConversionAttempt> findByIdAndJobId(UUID id, UUID jobId) {
        return jpaRepository.findByIdAndJobId(id, jobId);
    }

    @Override
    public List<ConversionAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId) {
        return jpaRepository.findByJobIdOrderByAttemptNumberAsc(jobId);
    }
}
