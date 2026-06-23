package com.docconverter.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface CleanupExpiredConversionUseCase {

    boolean cleanup(UUID jobId, Instant cleanupAt);
}
