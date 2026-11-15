package com.frameworkscanner.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.frameworkscanner.model.ScanResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the whole scan as {@code scan-result.json}, for CI checks and other tools. */
public final class JsonReport {

    private JsonReport() {}

    /**
     * The scan as JSON.
     *
     * @param result the scan
     * @param pretty indent the output
     * @return the JSON text
     */
    public static String toJson(ScanResult result, boolean pretty) {
        ObjectMapper mapper = new ObjectMapper();
        if (pretty) mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes {@code scan-result.json}.
     *
     * @param result the scan
     * @param dir    the report folder
     * @return the written file
     */
    public static Path write(ScanResult result, Path dir) {
        Path file = dir.resolve("scan-result.json");
        try {
            Files.writeString(file, toJson(result, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
        return file;
    }
}
