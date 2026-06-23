package com.docconverter.application.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@FunctionalInterface
public interface FileContentSource {

    InputStream openStream() throws IOException;

    static FileContentSource fromBytes(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] snapshot = content.clone();
        return () -> new ByteArrayInputStream(snapshot);
    }
}
