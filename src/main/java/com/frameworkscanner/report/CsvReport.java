package com.frameworkscanner.report;

import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.DuplicateGroup;
import com.frameworkscanner.model.Finding;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.ScanResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes three CSV files that open in Excel: {@code catalog.csv} (every method and what it does),
 * {@code duplicates.csv} (one row per duplicate method) and {@code recommendations.csv} (one row per finding).
 */
public final class CsvReport {

    private CsvReport() {}

    /**
     * Writes the CSV files.
     *
     * @param result the scan
     * @param dir    the report folder
     * @return the written files
     */
    public static List<Path> write(ScanResult result, Path dir) {
        Map<String, MethodInfo> byId = new HashMap<>();
        result.methods().forEach(m -> byId.put(m.id, m));
        List<Path> files = new ArrayList<>();

        List<List<String>> catalog = new ArrayList<>();
        catalog.add(List.of("Package", "Class", "Class role", "Method", "Signature", "Description", "Description source",
                "Purpose", "Step", "Calls", "Feature usages", "Author", "File", "Line"));
        for (ClassInfo c : result.classes) {
            for (MethodInfo m : c.methods) {
                catalog.add(List.of(c.packageName, c.name, c.role, m.name, m.signature, m.description,
                        m.descriptionSource, m.purpose, m.isStep() ? m.stepKeyword + " " + m.stepText : "",
                        String.valueOf(m.callCount), m.stepUsageCount < 0 ? "" : String.valueOf(m.stepUsageCount),
                        nz(m.author()), m.file, String.valueOf(m.beginLine)));
            }
        }
        files.add(write(dir.resolve("catalog.csv"), catalog));

        List<List<String>> dups = new ArrayList<>();
        dups.add(List.of("Group", "Level", "Keep", "Class", "Method", "Signature", "Lines", "Calls", "Author", "File",
                "Line", "Keep reason"));
        for (DuplicateGroup g : result.duplicateGroups) {
            for (String id : g.memberIds) {
                MethodInfo m = byId.get(id);
                dups.add(List.of(String.valueOf(g.number), g.levelLabel, id.equals(g.keepId) ? "KEEP" : "replace",
                        m.className, m.name, m.signature, String.valueOf(m.lines()), String.valueOf(m.callCount),
                        nz(m.author()), m.file, String.valueOf(m.beginLine), id.equals(g.keepId) ? g.keepReason : ""));
            }
        }
        files.add(write(dir.resolve("duplicates.csv"), dups));

        Map<String, RuleInfo> rules = result.rules.stream().collect(Collectors.toMap(RuleInfo::id, r -> r));
        List<List<String>> recs = new ArrayList<>();
        recs.add(List.of("Severity", "Category", "Rule", "Title", "Class", "Method", "File", "Line", "Message", "Author"));
        for (Finding f : result.findings) {
            RuleInfo r = rules.get(f.ruleId());
            recs.add(List.of(f.severity().name(), r.category(), f.ruleId(), r.title(), nz(f.className()),
                    nz(f.methodName()), f.file(), String.valueOf(f.line()), f.message(), nz(f.author())));
        }
        files.add(write(dir.resolve("recommendations.csv"), recs));
        return files;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static Path write(Path file, List<List<String>> rows) {
        StringBuilder out = new StringBuilder("﻿"); // BOM, so Excel reads UTF-8
        for (List<String> row : rows) {
            out.append(row.stream().map(CsvReport::cell).collect(Collectors.joining(","))).append("\r\n");
        }
        try {
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
        return file;
    }

    private static String cell(String value) {
        String v = value == null ? "" : value;
        if (v.matches("^[=+\\-@].*")) v = "'" + v; // no formula injection in Excel
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
