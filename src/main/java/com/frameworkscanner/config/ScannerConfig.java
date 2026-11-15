package com.frameworkscanner.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the scanner reads from {@code scanner.yaml}. Every section has defaults, so a minimal file only needs
 * {@code project.root}. Relative paths are resolved against the folder of the YAML file
 * ({@link #baseDir}), so the same file works from any working directory.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScannerConfig {

    /** Folder of the YAML file; set by {@link ConfigLoader}, not read from YAML. */
    public transient Path baseDir = Path.of(".");

    public Project project = new Project();
    public Library library = new Library();
    public Usages usages = new Usages();
    public Features features = new Features();
    public Duplicates duplicates = new Duplicates();
    public Rules rules = new Rules();
    public Report report = new Report();
    public Git git = new Git();

    /** The framework being scanned. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        /** Shown in the report title. */
        public String name = "Test automation framework";
        /** Root folder of the framework (relative to the YAML file or absolute). */
        public String root = ".";
    }

    /**
     * Which code is the "library": its methods go into the catalog, are compared for duplicates and are checked
     * against the rules.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Library {
        /** Source folders, relative to project.root. */
        public List<String> sourceDirs = new ArrayList<>(List.of("src/main/java", "src/test/java"));
        /** Packages to include, with their sub-packages. Empty means every package. */
        public List<String> includePackages = new ArrayList<>();
        /** Packages to leave out, with their sub-packages; wins over includePackages. */
        public List<String> excludePackages = new ArrayList<>();
        /** Glob patterns of files to leave out, for example "**&#47;generated/**". */
        public List<String> excludePaths = new ArrayList<>();
        /** Include private methods (false: leave them out, since other classes cannot reuse them anyway). */
        public boolean includePrivateMethods = true;
    }

    /** Where to count how often each library method is called (to see what is used and what to keep). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usages {
        /** Source folders searched for calls, relative to project.root. Empty means library.sourceDirs. */
        public List<String> sourceDirs = new ArrayList<>();
    }

    /** Cucumber feature files, used to count how often each step definition is used. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Features {
        /** Folders with .feature files, relative to project.root. Empty switches step usage counting off. */
        public List<String> dirs = new ArrayList<>(List.of("src/test/resources"));
    }

    /** Duplicate detection. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Duplicates {
        public boolean enabled = true;
        /** Methods with fewer statements are ignored (getters, setters, one-line delegates). */
        public int minStatements = 2;
        /** Methods with fewer code tokens are ignored. */
        public int minTokens = 25;
        /** Near copies: share of identical code fragments needed, 0..1. */
        public double nearMissThreshold = 0.80;
        /** Same job: share of identical UI/API actions in the same order needed, 0..1. */
        public double sameJobThreshold = 0.85;
        /** Same job: minimum number of UI/API actions a method needs to be compared. */
        public int minActions = 2;
    }

    /** Best-practice rules. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rules {
        public boolean enabled = true;
        /** Rule ids to switch off, for example [METHOD_NAME_VERB, REPEATED_LITERAL]. */
        public List<String> disabled = new ArrayList<>();
        public int longMethodLines = 40;
        public int maxParameters = 4;
        public int godClassMethods = 30;
        public int repeatedLiteralCount = 3;
        public int stringIfChainLength = 3;
    }

    /** Report output. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Report {
        /** Output folder, relative to the YAML file. */
        public String outputDir = "scanner-report";
        /** Any of html, json, csv. */
        public List<String> formats = new ArrayList<>(List.of("html", "json", "csv"));
    }

    /** Authors from git blame. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Git {
        /** Add the author of each method and finding (needs git on the PATH and project.root in a repo). */
        public boolean blame = true;
    }

    /** @return project.root as an absolute, normalised path */
    public Path projectRoot() {
        return baseDir.resolve(project.root).toAbsolutePath().normalize();
    }

    /** @return the report folder as an absolute path */
    public Path outputDir() {
        return baseDir.resolve(report.outputDir).toAbsolutePath().normalize();
    }

    /** @return the library source folders as absolute paths */
    public List<Path> librarySourceDirs() {
        return resolveAll(library.sourceDirs);
    }

    /** @return the folders searched for calls (library.sourceDirs if usages.sourceDirs is empty) */
    public List<Path> usageSourceDirs() {
        return usages.sourceDirs.isEmpty() ? librarySourceDirs() : resolveAll(usages.sourceDirs);
    }

    /** @return the feature file folders as absolute paths */
    public List<Path> featureDirs() {
        return resolveAll(features.dirs);
    }

    private List<Path> resolveAll(List<String> dirs) {
        Path root = projectRoot();
        return dirs.stream().map(d -> root.resolve(d).normalize()).toList();
    }
}
