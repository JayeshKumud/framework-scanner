package com.frameworkscanner;

import com.frameworkscanner.analysis.ActionExtractor;
import com.frameworkscanner.analysis.Describer;
import com.frameworkscanner.analysis.StepUsageCounter;
import com.frameworkscanner.analysis.UsageCounter;
import com.frameworkscanner.config.ScannerConfig;
import com.frameworkscanner.duplicates.DuplicateDetector;
import com.frameworkscanner.duplicates.TokenNormalizer;
import com.frameworkscanner.git.GitBlame;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.Finding;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.ScanResult;
import com.frameworkscanner.parse.ProjectParser;
import com.frameworkscanner.rules.RuleEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs one scan: parse the library, work out what every method does, count usages, find duplicates, check the
 * rules and add authors.
 */
public final class Scanner {

    private final ScannerConfig config;

    /**
     * Creates a scanner.
     *
     * @param config the loaded scanner.yaml
     */
    public Scanner(ScannerConfig config) {
        this.config = config;
    }

    /**
     * Scans the project.
     *
     * @return everything found
     */
    public ScanResult run() {
        long started = System.currentTimeMillis();
        ScanResult result = new ScanResult();
        result.projectName = config.project.name;
        result.projectRoot = config.projectRoot().toString();
        result.scannedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        result.includePackages = config.library.includePackages;

        ProjectParser parser = new ProjectParser(config);
        List<ClassInfo> classes = parser.parseLibrary();
        result.classes = classes;
        result.filesScanned = parser.filesScanned();

        analyse(classes);
        List<MethodInfo> methods = result.methods();

        UsageCounter.count(methods, ProjectParser.javaFiles(config.usageSourceDirs()), parser);
        StepUsageCounter.count(methods, ProjectParser.files(config.featureDirs(), ".feature"));
        result.parseErrors = parser.errors();

        if (config.duplicates.enabled) {
            result.duplicateGroups = new DuplicateDetector(config.duplicates).detect(methods);
        }
        RuleEngine engine = new RuleEngine();
        result.rules = engine.rules();
        if (config.rules.enabled) {
            result.findings = engine.check(classes, config.rules);
        }

        GitBlame blame = new GitBlame(config.projectRoot(), config.git.blame);
        if (blame.available()) {
            methods.parallelStream().forEach(m -> m.authors = blame.authors(m.file, m.beginLine, m.endLine));
            result.findings = result.findings.stream()
                    .map(f -> f.withAuthor(blame.author(f.file(), f.line()))).toList();
        }
        result.durationMs = System.currentTimeMillis() - started;
        return result;
    }

    private static void analyse(List<ClassInfo> classes) {
        Map<String, String> constants = new HashMap<>();
        Set<String> methodNames = new HashSet<>();
        for (ClassInfo c : classes) {
            c.fieldValues.forEach((name, value) -> constants.putIfAbsent(c.name + "." + name, value));
            c.methods.forEach(m -> methodNames.add(m.name));
        }
        for (ClassInfo c : classes) {
            ActionExtractor extractor = new ActionExtractor(c, constants, methodNames);
            for (MethodInfo m : c.methods) {
                m.actions = extractor.extract(m.node);
                TokenNormalizer.tokenize(m);
                Describer.describe(m);
            }
            c.role = Describer.role(c);
        }
    }

    /**
     * Counts findings at or above a severity.
     *
     * @param result   the scan
     * @param severity the lowest severity counted
     * @return the number of findings
     */
    public static long countAtLeast(ScanResult result, com.frameworkscanner.model.Severity severity) {
        return result.findings.stream().map(Finding::severity).filter(s -> s.ordinal() <= severity.ordinal()).count();
    }
}
