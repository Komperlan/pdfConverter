package com.docconverter.testsupport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TestDocuments {

    public static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private TestDocuments() {
    }

    public static byte[] minimalDocx() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                addEntry(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                            <Default Extension="xml" ContentType="application/xml"/>
                            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                        </Types>
                        """);
                addEntry(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                            <Relationship Id="rId1"
                                          Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                                          Target="word/document.xml"/>
                        </Relationships>
                        """);
                addEntry(zip, "word/document.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                            <w:body><w:p><w:r><w:t>DocConverter</w:t></w:r></w:p></w:body>
                        </w:document>
                        """);
            }
            return output.toByteArray();
        } catch (Exception exc) {
            throw new IllegalStateException("Failed to create test DOCX document", exc);
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
