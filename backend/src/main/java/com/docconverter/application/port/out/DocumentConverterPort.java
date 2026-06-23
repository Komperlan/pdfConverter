package com.docconverter.application.port.out;

import com.docconverter.application.conversion.ConvertDocumentCommand;
import com.docconverter.application.conversion.ConvertedDocument;

public interface DocumentConverterPort {

    /**
     * Converts a source DOC/DOCX document to PDF.
     * Implementations must close every input stream they open. The returned content source
     * must remain readable after this method returns.
     */
    ConvertedDocument convert(ConvertDocumentCommand command);
}
