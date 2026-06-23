package com.docconverter.application.services;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.docconverter.application.port.in.GetConversionJobUseCase;
import com.docconverter.application.port.in.SubmitConversionCommand;
import com.docconverter.application.port.in.SubmitConversionUseCase;
import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.application.port.out.FileStoragePort;
import com.docconverter.application.storage.StoreFileCommand;
import com.docconverter.application.storage.StoredFile;
import com.docconverter.application.storage.StoredFilePurpose;
import com.docconverter.application.validation.FileValidator;
import com.docconverter.application.validation.ValidateFileCommand;
import com.docconverter.application.validation.ValidatedFile;
import com.docconverter.domain.exception.ConversionJobNotFoundException;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.infrastructure.config.ConversionJobProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversionJobService implements SubmitConversionUseCase, GetConversionJobUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionJobService.class);
    private final ConversionJobRepository conversionJobRepository;
    private final FileStoragePort fileStoragePort;
    private final FileValidator fileValidator;
    private final ConversionJobProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public ConversionJob submit(SubmitConversionCommand command) {
        ValidatedFile validatedFile = fileValidator.validate(new ValidateFileCommand(
                command.originalFilename(),
                command.declaredMimeType(),
                command.sizeBytes(),
                command.contentSource()
        ));
        StoredFile storedFile = fileStoragePort.save(new StoreFileCommand(
                StoredFilePurpose.SOURCE_DOCUMENT,
                validatedFile.safeFilename(),
                validatedFile.fileExtension(),
                validatedFile.detectedMimeType(),
                validatedFile.sizeBytes(),
                validatedFile.contentSource()
        ));

        boolean rollbackCleanupRegistered;
        try {
            rollbackCleanupRegistered = registerRollbackCleanup(storedFile.storagePath());
        } catch (RuntimeException | Error exc) {
            deleteAfterFailure(storedFile.storagePath(), exc);
            throw exc;
        }

        try {
            return saveNewJob(validatedFile, storedFile);
        } catch (RuntimeException | Error exc) {
            if (!rollbackCleanupRegistered) {
                deleteAfterFailure(storedFile.storagePath(), exc);
            }
            throw exc;
        }
    }

    private ConversionJob saveNewJob(ValidatedFile validatedFile, StoredFile storedFile) {
        Instant createdAt = Instant.now(clock);
        ConversionJob job = ConversionJob.create(
                validatedFile.originalFilename(),
                validatedFile.safeFilename(),
                storedFile.storagePath(),
                validatedFile.fileExtension(),
                validatedFile.declaredMimeType(),
                validatedFile.detectedMimeType(),
                storedFile.sizeBytes(),
                storedFile.checksumSha256(),
                createdAt,
                createdAt.plus(properties.getExpiration()),
                properties.getMaxAttempts()
        );

        return conversionJobRepository.save(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversionJob getById(UUID id) {
        return conversionJobRepository.findById(id)
                .orElseThrow(() -> new ConversionJobNotFoundException(id));
    }

    private boolean registerRollbackCleanup(String storagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteAfterRollback(storagePath);
                }
            }
        });
        return true;
    }

    private void deleteAfterFailure(String storagePath, Throwable originalFailure) {
        try {
            fileStoragePort.delete(storagePath);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            LOGGER.error("Failed to delete source file after job creation failure", cleanupFailure);
        }
    }

    private void deleteAfterRollback(String storagePath) {
        try {
            fileStoragePort.delete(storagePath);
        } catch (RuntimeException cleanupFailure) {
            LOGGER.error("Failed to delete source file after transaction rollback", cleanupFailure);
        }
    }
}
