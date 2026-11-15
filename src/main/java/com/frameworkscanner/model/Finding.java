package com.frameworkscanner.model;

/**
 * One place where the code breaks a best-practice rule. The rule's explanation and before/after example are in
 * {@link RuleInfo}, once per rule, so findings stay small.
 *
 * @param ruleId     the rule, see {@link RuleInfo#id()}
 * @param severity   the rule's severity
 * @param file       path relative to project.root
 * @param line       source line
 * @param className  the class, or null
 * @param methodName the method, or null for class-level findings
 * @param message    what is wrong here, specifically
 * @param author     main author of the line (git blame), or null
 */
public record Finding(String ruleId, Severity severity, String file, int line, String className, String methodName,
                      String message, String author) {

    /**
     * A copy with the author set.
     *
     * @param author the author
     * @return the new finding
     */
    public Finding withAuthor(String author) {
        return new Finding(ruleId, severity, file, line, className, methodName, message, author);
    }
}
