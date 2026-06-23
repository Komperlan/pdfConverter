package com.docconverter.application.port.in;

import java.util.UUID;

public interface ProcessConversionUseCase {

    void process(UUID jobId);
}
