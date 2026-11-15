package com.frameworkscanner.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads {@code scanner.yaml} and checks it. */
public final class ConfigLoader {

    private static final List<String> FORMATS = List.of("html", "json", "csv");

    private ConfigLoader() {}

    /**
     * Reads a config file.
     *
     * @param file the YAML file
     * @return the config, with {@code baseDir} set to the file's folder
     * @throws IllegalArgumentException if the file is missing or a value is invalid
     */
    public static ScannerConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Config file not found: " + file.toAbsolutePath());
        }
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        ScannerConfig config;
        try {
            config = yaml.readValue(file.toFile(), ScannerConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        if (config == null) config = new ScannerConfig(); // empty file: all defaults
        config.baseDir = file.toAbsolutePath().getParent();
        validate(config, file);
        return config;
    }

    private static void validate(ScannerConfig config, Path file) {
        Path root = config.projectRoot();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(file + ": project.root does not exist: " + root);
        }
        if (config.librarySourceDirs().stream().noneMatch(Files::isDirectory)) {
            throw new IllegalArgumentException(file + ": none of library.sourceDirs exists under " + root + ": "
                    + config.library.sourceDirs);
        }
        ScannerConfig.Duplicates d = config.duplicates;
        requireShare(file, "duplicates.nearMissThreshold", d.nearMissThreshold);
        requireShare(file, "duplicates.sameJobThreshold", d.sameJobThreshold);
        for (String format : config.report.formats) {
            if (!FORMATS.contains(format.toLowerCase())) {
                throw new IllegalArgumentException(file + ": unknown report format '" + format + "'. Use " + FORMATS);
            }
        }
    }

    private static void requireShare(Path file, String name, double value) {
        if (value <= 0 || value > 1) {
            throw new IllegalArgumentException(file + ": " + name + " must be between 0 and 1, got " + value);
        }
    }
}
