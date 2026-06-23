package com.docconverter.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface RecoverStalledConversionUseCase {

    boolean recover(UUID jobId, Instant staleBefore, Instant recoveredAt);
}
