package com.docconverter.application.port.in;

import com.docconverter.domain.model.ConversionJob;
import java.util.UUID;

public interface GetConversionJobUseCase {

    ConversionJob getById(UUID id);
}
