package com.docconverter.application.services;

import com.docconverter.application.port.in.GetConversionResultUseCase;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.application.result.ConversionResult;
import com.docconverter.application.storage.StoredFileContent;
import com.docconverter.domain.exception.ConversionJobNotFoundException;
import com.docconverter.domain.exception.ConversionResultExpiredException;
import com.docconverter.domain.exception.ConversionResultNotReadyException;
import com.docconverter.domain.exception.ConversionResultUnavailableException;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversionResultService implements GetConversionResultUseCase {

    private final ConversionJobRepository conversionJobRepository;
    private final FileStoragePort fileStoragePort;
    private final Clock clock;

    @Override
    public ConversionResult getResult(UUID jobId) {
        ConversionJob job = conversionJobRepository.findById(jobId)
                .orElseThrow(() -> new ConversionJobNotFoundException(jobId));

        if (job.getStatus() != ConversionStatus.PROCESSING
                && !job.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ConversionResultExpiredException(jobId);
        }

        switch (job.getStatus()) {
            case CREATED, PROCESSING -> throw new ConversionResultNotReadyException(
                    jobId,
                    job.getStatus()
            );
            case FAILED -> throw new ConversionResultUnavailableException(
                    jobId,
                    job.getLastErrorCode(),
                    job.getLastErrorMessage()
            );
            case EXPIRED -> throw new ConversionResultExpiredException(jobId);
            case COMPLETED -> {
                // Continue below and load the stored PDF outside a database transaction.
            }
        }

        String resultFilePath = job.getResultFilePath();
        if (resultFilePath == null || resultFilePath.isBlank()) {
            throw new IllegalStateException("Completed conversion job has no result file path");
        }

        StoredFileContent storedFile = fileStoragePort.load(resultFilePath);
        return new ConversionResult(
                resultFilename(job.getSafeOriginalFilename()),
                storedFile.mediaType(),
                storedFile.sizeBytes(),
                storedFile.contentSource()
        );
    }

    private String resultFilename(String sourceFilename) {
        int extensionIndex = sourceFilename.lastIndexOf('.');
        String baseName = extensionIndex > 0
                ? sourceFilename.substring(0, extensionIndex)
                : sourceFilename;
        return baseName + ".pdf";
    }
}
