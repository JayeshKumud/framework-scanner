package com.frameworkscanner;

import com.frameworkscanner.config.ConfigLoader;
import com.frameworkscanner.config.ScannerConfig;
import com.frameworkscanner.model.ScanResult;
import com.frameworkscanner.model.Severity;
import com.frameworkscanner.report.ConsoleSummary;
import com.frameworkscanner.report.CsvReport;
import com.frameworkscanner.report.HtmlReport;
import com.frameworkscanner.report.JsonReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Command line entry point.
 * <pre>
 * java -jar framework-scanner.jar [--config scanner.yaml] [--fail-on-duplicates] [--fail-on-severity HIGH]
 * </pre>
 * Exit codes: 0 done, 1 error (bad config), 2 duplicates found with --fail-on-duplicates, 3 findings at or above
 * the --fail-on-severity level.
 */
public final class Main {

    private Main() {}

    /**
     * Runs a scan.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Runs a scan and returns the exit code (testable without System.exit).
     *
     * @param args command line arguments
     * @return the exit code
     */
    public static int run(String[] args) {
        Path config = Path.of("scanner.yaml");
        boolean failOnDuplicates = false;
        Severity failOnSeverity = null;
        try {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config", "-c" -> config = Path.of(value(args, ++i, "--config"));
                case "--fail-on-duplicates" -> failOnDuplicates = true;
                case "--fail-on-severity" -> failOnSeverity = Severity.valueOf(value(args, ++i, "--fail-on-severity")
                        .toUpperCase(Locale.ROOT));
                case "--help", "-h" -> {
                    System.out.println(usage());
                    return 0;
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i] + "\n" + usage());
                    return 1;
                }
            }
        }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage() + "\n" + usage());
            return 1;
        }
        try {
            ScannerConfig cfg = ConfigLoader.load(config);
            System.out.println("Scanning " + cfg.projectRoot() + " ...");
            ScanResult result = new Scanner(cfg).run();
            Path out = cfg.outputDir();
            Files.createDirectories(out);
            for (String format : cfg.report.formats) {
                switch (format.toLowerCase(Locale.ROOT)) {
                    case "html" -> System.out.println("HTML report: " + HtmlReport.write(result, out));
                    case "json" -> System.out.println("JSON:        " + JsonReport.write(result, out));
                    case "csv" -> CsvReport.write(result, out).forEach(f -> System.out.println("CSV:         " + f));
                    default -> { }
                }
            }
            System.out.print(ConsoleSummary.of(result));
            if (failOnDuplicates && !result.duplicateGroups.isEmpty()) {
                System.err.println("Failing: " + result.duplicateGroups.size() + " duplicate group(s) found.");
                return 2;
            }
            if (failOnSeverity != null) {
                long n = Scanner.countAtLeast(result, failOnSeverity);
                if (n > 0) {
                    System.err.println("Failing: " + n + " finding(s) at " + failOnSeverity + " or above.");
                    return 3;
                }
            }
            return 0;
        } catch (IllegalArgumentException | IOException | java.io.UncheckedIOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) throw new IllegalArgumentException(option + " needs a value");
        return args[i];
    }

    private static String usage() {
        return """
                Usage: java -jar framework-scanner.jar [options]
                  --config, -c FILE          scanner config (default: scanner.yaml)
                  --fail-on-duplicates       exit with code 2 if any duplicate group is found
                  --fail-on-severity LEVEL   exit with code 3 if a finding is at LEVEL or above (HIGH, MEDIUM, LOW, INFO)
                  --help, -h                 show this help""";
    }
}
