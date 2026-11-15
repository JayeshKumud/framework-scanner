package com.frameworkscanner.rules;

import com.frameworkscanner.config.ScannerConfig;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.Finding;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What a rule set sees: the scanned classes, the rule settings, and where to report findings. */
public final class RuleContext {

    private final List<ClassInfo> classes;
    private final ScannerConfig.Rules settings;
    private final Map<String, RuleInfo> rules;
    private final Set<String> disabled;
    private final List<Finding> findings = new ArrayList<>();

    RuleContext(List<ClassInfo> classes, ScannerConfig.Rules settings, Map<String, RuleInfo> rules) {
        this.classes = classes;
        this.settings = settings;
        this.rules = rules;
        this.disabled = Set.copyOf(settings.disabled.stream().map(String::trim).map(String::toUpperCase).toList());
    }

    /** @return every library class */
    public List<ClassInfo> classes() {
        return classes;
    }

    /** @return every library method */
    public List<MethodInfo> methods() {
        List<MethodInfo> all = new ArrayList<>();
        classes.forEach(c -> all.addAll(c.methods));
        return all;
    }

    /** @return the rules section of scanner.yaml (thresholds) */
    public ScannerConfig.Rules settings() {
        return settings;
    }

    /**
     * Tells whether a rule is switched on.
     *
     * @param ruleId the rule id
     * @return false if listed under rules.disabled
     */
    public boolean enabled(String ruleId) {
        return !disabled.contains(ruleId);
    }

    /**
     * Reports a finding in a method.
     *
     * @param ruleId  the rule
     * @param method  the method
     * @param node    the node to point at (its line), or null for the method's first line
     * @param message what is wrong here
     */
    public void report(String ruleId, MethodInfo method, Node node, String message) {
        int line = node == null ? method.beginLine : node.getBegin().map(p -> p.line).orElse(method.beginLine);
        add(ruleId, method.file, line, method.className, method.name, message);
    }

    /**
     * Reports a finding on a line of a method.
     *
     * @param ruleId  the rule
     * @param method  the method
     * @param line    the line
     * @param message what is wrong here
     */
    public void report(String ruleId, MethodInfo method, int line, String message) {
        add(ruleId, method.file, line > 0 ? line : method.beginLine, method.className, method.name, message);
    }

    /**
     * Reports a finding in a class.
     *
     * @param ruleId  the rule
     * @param cls     the class
     * @param node    the node to point at, or null for the class's first line
     * @param message what is wrong here
     */
    public void report(String ruleId, ClassInfo cls, Node node, String message) {
        int line = node == null ? cls.beginLine : node.getBegin().map(p -> p.line).orElse(cls.beginLine);
        add(ruleId, cls.file, line, cls.name, enclosingMethod(node), message);
    }

    private static String enclosingMethod(Node node) {
        if (node == null) return null;
        return node.findAncestor(com.github.javaparser.ast.body.MethodDeclaration.class)
                .map(NodeWithSimpleName::getNameAsString).orElse(null);
    }

    private void add(String ruleId, String file, int line, String cls, String method, String message) {
        RuleInfo rule = rules.get(ruleId);
        if (rule == null) throw new IllegalStateException("Unknown rule " + ruleId);
        if (!enabled(ruleId)) return;
        findings.add(new Finding(ruleId, rule.severity(), file, line, cls, method, message, null));
    }

    List<Finding> findings() {
        return findings;
    }
}
