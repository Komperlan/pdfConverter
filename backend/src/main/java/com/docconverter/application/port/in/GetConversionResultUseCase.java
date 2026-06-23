package com.docconverter.application.port.in;

import com.docconverter.application.result.ConversionResult;
import java.util.UUID;

public interface GetConversionResultUseCase {

    ConversionResult getResult(UUID jobId);
}
