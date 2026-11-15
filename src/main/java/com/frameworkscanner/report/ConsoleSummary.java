package com.frameworkscanner.report;

import com.frameworkscanner.model.DuplicateGroup;
import com.frameworkscanner.model.Finding;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.ScanResult;
import com.frameworkscanner.model.Severity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The short summary printed after a scan. */
public final class ConsoleSummary {

    private ConsoleSummary() {}

    /**
     * Builds the summary text.
     *
     * @param r the scan
     * @return several lines
     */
    public static String of(ScanResult r) {
        List<MethodInfo> methods = r.methods();
        long documented = methods.stream().filter(m -> m.javadoc != null).count();
        long unused = methods.stream().filter(ConsoleSummary::isUnused).count();
        int inDuplicates = r.duplicateGroups.stream().mapToInt(g -> g.memberIds.size()).sum();
        int removable = r.duplicateGroups.stream().mapToInt(g -> g.removableLines).sum();
        Map<Severity, Long> bySeverity = new EnumMap<>(Severity.class);
        for (Finding f : r.findings) bySeverity.merge(f.severity(), 1L, Long::sum);
        Map<String, Long> byLevel = new TreeMap<>();
        for (DuplicateGroup g : r.duplicateGroups) byLevel.merge(g.levelLabel, 1L, Long::sum);

        StringBuilder out = new StringBuilder();
        out.append("Scanned ").append(r.filesScanned).append(" files in ").append(r.durationMs).append(" ms: ")
                .append(r.classes.size()).append(" classes, ").append(methods.size()).append(" methods (")
                .append(pct(documented, methods.size())).append(" with Javadoc, ").append(unused)
                .append(" never called).\n");
        out.append("Duplicates: ").append(r.duplicateGroups.size()).append(" groups ").append(byLevel)
                .append(", ").append(inDuplicates).append(" methods, about ").append(removable)
                .append(" lines removable.\n");
        out.append("Recommendations: ").append(r.findings.size()).append(" ").append(bySeverity).append(".\n");
        if (!r.parseErrors.isEmpty()) {
            out.append("Could not parse ").append(r.parseErrors.size()).append(" file(s), first: ")
                    .append(r.parseErrors.get(0)).append('\n');
        }
        return out.toString();
    }

    /**
     * Tells whether a method is never used (steps: no feature line; others: no call found).
     *
     * @param m the method
     * @return true if unused
     */
    public static boolean isUnused(MethodInfo m) {
        if (m.isStep()) return m.stepUsageCount == 0;
        if (m.annotations.contains("Test") || m.annotations.contains("Override") || m.name.equals("main")) return false;
        if (m.annotations.stream().anyMatch(a -> a.startsWith("Before") || a.startsWith("After"))) return false;
        return m.callCount == 0;
    }

    private static String pct(long part, long total) {
        return total == 0 ? "0%" : Math.round(100.0 * part / total) + "%";
    }
}
