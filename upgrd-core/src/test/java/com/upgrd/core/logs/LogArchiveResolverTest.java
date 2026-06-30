package com.upgrd.core.logs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogArchiveResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void assignsUniqueNamesForDuplicateBasenamesAcrossArchives() throws Exception {
        Path logsDir = tempDir.resolve("archives");
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(logsDir);

        writeZip(logsDir.resolve("week1.zip"), "access.log", "GET /a 200\n");
        writeZip(logsDir.resolve("week2.zip"), "access.log", "GET /b 500\n");
        Files.writeString(logsDir.resolve("server.log"), "INFO ok\n");

        var manifest = new LogArchiveResolver().resolve(logsDir, staging);

        assertEquals(3, manifest.entries().size());
        Set<String> stagedNames = manifest.entries().stream()
                .map(e -> e.stagedFile())
                .collect(Collectors.toSet());
        assertEquals(3, stagedNames.size(), "each extracted file must have a unique staged name");
        assertTrue(stagedNames.stream().allMatch(name -> name.contains("__")));
        assertTrue(Files.isRegularFile(staging.resolve(manifest.entries().get(0).stagedFile())));
    }

    private void writeZip(Path zipPath, String entryName, String content) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
    }
}
