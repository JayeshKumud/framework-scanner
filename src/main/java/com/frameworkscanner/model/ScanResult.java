package com.frameworkscanner.model;

import java.util.ArrayList;
import java.util.List;

/** Everything one scan found; the reports are written from this. */
public class ScanResult {

    public String projectName;
    public String projectRoot;
    public String scannedAt;
    public long durationMs;
    public List<String> includePackages = new ArrayList<>();
    public int filesScanned;
    /** Files that could not be parsed, with the reason. */
    public List<String> parseErrors = new ArrayList<>();

    public List<ClassInfo> classes = new ArrayList<>();
    public List<DuplicateGroup> duplicateGroups = new ArrayList<>();
    public List<RuleInfo> rules = new ArrayList<>();
    public List<Finding> findings = new ArrayList<>();

    /** @return every method of every class */
    public List<MethodInfo> methods() {
        List<MethodInfo> all = new ArrayList<>();
        classes.forEach(c -> all.addAll(c.methods));
        return all;
    }
}
