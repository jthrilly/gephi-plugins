package uk.ac.ox.oii.sigmaexporter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipHandlerTest {

    @TempDir
    Path tmp;

    private static byte[] zip(boolean withDirEntries, String... namesAndContents) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            if (withDirEntries) {
                zos.putNextEntry(new ZipEntry("network/"));
                zos.closeEntry();
            }
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zos.putNextEntry(new ZipEntry(namesAndContents[i]));
                zos.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    void extractsNestedHashedFilesWithoutDirectoryEntries() throws IOException {
        byte[] z = zip(false,
                "network/index.html", "<html></html>",
                "network/assets/index-abc123.js", "console.log(1)",
                "network/assets/index-def456.css", "body{}",
                "network/images/logo.png", "png");
        ZipHandler.extractZip(new ByteArrayInputStream(z), tmp.toString());
        assertEquals("console.log(1)", Files.readString(tmp.resolve("network/assets/index-abc123.js")));
        assertEquals("body{}", Files.readString(tmp.resolve("network/assets/index-def456.css")));
        assertArrayEquals("png".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(tmp.resolve("network/images/logo.png")));
        assertEquals("<html></html>", Files.readString(tmp.resolve("network/index.html")));
    }

    @Test
    void overwritesExistingFiles() throws IOException {
        Files.createDirectories(tmp.resolve("network"));
        Files.writeString(tmp.resolve("network/index.html"), "old and longer content");
        ZipHandler.extractZip(new ByteArrayInputStream(zip(true, "network/index.html", "new")), tmp.toString());
        assertEquals("new", Files.readString(tmp.resolve("network/index.html")));
    }

    @Test
    void rejectsEntriesOutsideTarget() throws IOException {
        byte[] z = zip(false, "../evil.txt", "x");
        Path dest = tmp.resolve("out");
        assertThrows(IOException.class, () -> ZipHandler.extractZip(new ByteArrayInputStream(z), dest.toString()));
        assertFalse(Files.exists(tmp.resolve("evil.txt")));
    }

    @Test
    void bundledTemplateHasNetworkRoot() throws IOException {
        ZipHandler.extractZip(SigmaExporter.class.getResourceAsStream("resources/network.zip"), tmp.toString());
        assertEquals(true, Files.isRegularFile(tmp.resolve("network/index.html")));
    }

    @Test
    void refusesToWriteThroughASymlinkedFolder() throws IOException {
        Path outside = Files.createDirectory(tmp.resolve("outside"));
        Path dest = Files.createDirectory(tmp.resolve("export"));
        Files.createSymbolicLink(dest.resolve("network"), outside);
        byte[] z = zip(false, "network/index.html", "<html></html>");
        assertThrows(IOException.class, () -> ZipHandler.extractZip(new ByteArrayInputStream(z), dest.toString()));
        assertFalse(Files.exists(outside.resolve("index.html")));
    }
}
