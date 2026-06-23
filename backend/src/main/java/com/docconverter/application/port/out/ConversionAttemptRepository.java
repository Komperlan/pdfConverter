package com.docconverter.application.port.out;

import com.docconverter.domain.model.ConversionAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversionAttemptRepository {

    ConversionAttempt save(ConversionAttempt attempt);

    Optional<ConversionAttempt> findByIdAndJobId(UUID id, UUID jobId);

    List<ConversionAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId);
}
