package com.docconverter.application.port.in;

import com.docconverter.domain.model.ConversionJob;

public interface SubmitConversionUseCase {

    ConversionJob submit(SubmitConversionCommand command);
}
