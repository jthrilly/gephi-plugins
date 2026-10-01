/*
 Copyright Scott A. Hale, 2016
 */
package uk.ac.ox.oii.sigmaexporter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts the bundled viewer template. Works for any file list: entries may
 * live in nested folders (e.g. network/assets/index-abc123.js) and the zip does
 * not need explicit directory entries.
 */
public class ZipHandler {

    private static final Logger LOG = Logger.getLogger(ZipHandler.class.getName());

    public static void extractZip(InputStream input, String dest) throws IOException {
        if (input == null) {
            throw new IOException("Viewer template (network.zip) not found in the plugin");
        }
        Path destDir = new File(dest).toPath().toAbsolutePath().normalize();
        Files.createDirectories(destDir);
        try (ZipInputStream zin = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zin, target, StandardCopyOption.REPLACE_EXISTING);
                    LOG.log(Level.FINE, "Extracted {0}", entry.getName());
                }
                zin.closeEntry();
            }
        }
    }
}
