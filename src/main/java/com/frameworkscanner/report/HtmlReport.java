package com.frameworkscanner.report;

import com.frameworkscanner.model.ScanResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@code report.html}: one self-contained file (no internet needed, nothing loaded from outside) with
 * four tabs: overview, library catalog, duplicates and recommendations. The scan data is embedded as JSON and
 * rendered by plain JavaScript, so the file can be mailed or attached to a CI build.
 */
public final class HtmlReport {

    private static final String TEMPLATE = "/report-template.html";
    private static final String PLACEHOLDER = "/*__DATA__*/";

    private HtmlReport() {}

    /**
     * Writes the report.
     *
     * @param result the scan
     * @param dir    the report folder
     * @return the written file
     */
    public static Path write(ScanResult result, Path dir) {
        String template;
        try (InputStream in = HtmlReport.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) throw new IllegalStateException("Missing resource " + TEMPLATE);
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // "<" only occurs inside JSON strings, where < means the same; this keeps "</script>" in source code
        // from ending the script element.
        String json = JsonReport.toJson(result, false).replace("<", "\\u003c");
        String html = template.replace(PLACEHOLDER, json);
        Path file = dir.resolve("report.html");
        try {
            Files.writeString(file, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
        return file;
    }
}
