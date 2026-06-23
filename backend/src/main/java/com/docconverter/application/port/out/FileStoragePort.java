package com.docconverter.application.port.out;

import com.docconverter.application.storage.StoreFileCommand;
import com.docconverter.application.storage.StoredFile;
import com.docconverter.application.storage.StoredFileContent;

public interface FileStoragePort {

    StoredFile save(StoreFileCommand command);

    StoredFileContent load(String storagePath);

    void delete(String storagePath);
}
