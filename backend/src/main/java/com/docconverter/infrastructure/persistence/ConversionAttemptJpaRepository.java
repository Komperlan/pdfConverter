package com.docconverter.infrastructure.persistence;

import com.docconverter.domain.model.ConversionAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionAttemptJpaRepository extends JpaRepository<ConversionAttempt, UUID> {

    List<ConversionAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId);

    Optional<ConversionAttempt> findByIdAndJobId(UUID id, UUID jobId);
}
