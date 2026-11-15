package com.frameworkscanner.analysis;

import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.parse.ProjectParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts how often each library method is called, so the report can show which methods are used (and which one
 * of a duplicate group to keep) and which are never called.
 * <p>
 * Without compiling the framework, calls are matched by method name and number of arguments. Two methods with
 * the same name and argument count in different classes therefore share their count; the report says the
 * count is approximate.
 */
public final class UsageCounter {

    private UsageCounter() {}

    /**
     * Sets {@link MethodInfo#callCount} of every method.
     *
     * @param methods the library methods
     * @param files   the Java files to search
     * @param parser  the parser (its cache is reused)
     */
    public static void count(List<MethodInfo> methods, List<Path> files, ProjectParser parser) {
        Map<String, Integer> byNameAndArity = new HashMap<>();
        Map<String, Integer> references = new HashMap<>();
        for (Path file : files) {
            CompilationUnit unit = parser.parse(file);
            if (unit == null) continue;
            for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                byNameAndArity.merge(call.getNameAsString() + "/" + call.getArguments().size(), 1, Integer::sum);
            }
            for (MethodReferenceExpr ref : unit.findAll(MethodReferenceExpr.class)) {
                references.merge(ref.getIdentifier(), 1, Integer::sum);
            }
        }
        for (MethodInfo m : methods) {
            int arity = m.parameterTypes.size();
            boolean varargs = !m.parameterTypes.isEmpty() && m.parameterTypes.get(arity - 1).endsWith("...");
            int count = byNameAndArity.getOrDefault(m.name + "/" + arity, 0);
            if (varargs) {
                for (int extra = 0; extra <= 5; extra++) {
                    if (extra == 1) continue; // arity itself, counted above
                    count += byNameAndArity.getOrDefault(m.name + "/" + (arity - 1 + extra), 0);
                }
            }
            m.callCount = count + references.getOrDefault(m.name, 0);
        }
    }
}
