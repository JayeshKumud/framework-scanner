package com.frameworkscanner.rules;

import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.Severity;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Java features that make the code shorter and safer: enums and switch, Optional, StringBuilder. */
final class JavaIdiomRules implements RuleSet {

    static final String STRING_IF_CHAIN = "STRING_IF_CHAIN";
    static final String RETURN_NULL = "RETURN_NULL";
    static final String STRING_CONCAT_LOOP = "STRING_CONCAT_IN_LOOP";

    private static final Set<String> PRIMITIVES = Set.of("void", "int", "long", "double", "float", "boolean", "char",
            "byte", "short");

    @Override
    public List<RuleInfo> rules() {
        return List.of(
                new RuleInfo(STRING_IF_CHAIN, "Enum and switch instead of if-else on strings", "Java idioms", Severity.LOW,
                        "A chain of if (x.equals(\"chrome\")) else if (x.equals(\"firefox\")) ... accepts any typo "
                                + "and grows in every copy. An enum lists the allowed values once, and a switch "
                                + "expression on it is checked by the compiler.",
                        "if (browser.equals(\"chrome\")) { ... }\nelse if (browser.equals(\"firefox\")) { ... }\nelse if (browser.equals(\"webkit\")) { ... }",
                        "enum BrowserName { CHROME, FIREFOX, WEBKIT }\n\nBrowser b = switch (BrowserName.valueOf(name.toUpperCase())) {\n    case CHROME -> playwright.chromium().launch();\n    case FIREFOX -> playwright.firefox().launch();\n    case WEBKIT -> playwright.webkit().launch();\n};"),
                new RuleInfo(RETURN_NULL, "Optional instead of returning null", "Java idioms", Severity.LOW,
                        "A method that returns null for \"not found\" forces every caller to remember a null check; "
                                + "a forgotten one is a NullPointerException far from the cause. Optional says it in "
                                + "the signature.",
                        "public User findUser(String name) {\n    ...\n    return null;\n}",
                        "public Optional<User> findUser(String name) {\n    ...\n    return Optional.empty();\n}\n\nfindUser(\"ada\").orElseThrow(() -> new IllegalStateException(\"No user ada\"));"),
                new RuleInfo(STRING_CONCAT_LOOP, "StringBuilder or String.join in loops", "Java idioms", Severity.LOW,
                        "s += x in a loop creates a new string every time. StringBuilder, String.join or "
                                + "Collectors.joining are faster and clearer.",
                        "String names = \"\";\nfor (User u : users) {\n    names += u.name() + \",\";\n}",
                        "String names = users.stream().map(User::name).collect(Collectors.joining(\",\"));"));
    }

    @Override
    public void check(RuleContext ctx) {
        int chain = ctx.settings().stringIfChainLength;
        for (MethodInfo m : ctx.methods()) {
            for (IfStmt ifStmt : m.node.findAll(IfStmt.class)) {
                if (isElseOfParent(ifStmt)) continue;
                int length = stringChainLength(ifStmt);
                if (length >= chain) {
                    ctx.report(STRING_IF_CHAIN, m, ifStmt, length + " if/else-if branches compare the same string; "
                            + "use an enum with a switch.");
                }
            }
            if (!PRIMITIVES.contains(m.returnType) && !m.returnType.startsWith("Optional")) {
                for (ReturnStmt r : m.node.findAll(ReturnStmt.class)) {
                    if (r.getExpression().map(Expression::isNullLiteralExpr).orElse(false) && ownReturn(m, r)) {
                        ctx.report(RETURN_NULL, m, r, m.name + " returns null; return Optional<" + m.returnType + "> instead.");
                        break;
                    }
                }
            }
            Set<String> strings = new HashSet<>();
            for (VariableDeclarationExpr d : m.node.findAll(VariableDeclarationExpr.class)) {
                d.getVariables().forEach(v -> {
                    if (v.getTypeAsString().equals("String")) strings.add(v.getNameAsString());
                });
            }
            for (AssignExpr a : m.node.findAll(AssignExpr.class)) {
                if (a.getOperator() == AssignExpr.Operator.PLUS && a.getTarget() instanceof NameExpr n
                        && strings.contains(n.getNameAsString()) && inLoop(a)) {
                    ctx.report(STRING_CONCAT_LOOP, m, a, n.getNameAsString() + " += ... inside a loop.");
                }
            }
        }
    }

    private static boolean ownReturn(MethodInfo m, ReturnStmt r) {
        // a return inside a lambda or anonymous class belongs to that, not to the method
        return r.findAncestor(com.github.javaparser.ast.expr.LambdaExpr.class).isEmpty()
                && r.findAncestor(com.github.javaparser.ast.body.MethodDeclaration.class).map(x -> x == m.node).orElse(false);
    }

    private static boolean isElseOfParent(IfStmt ifStmt) {
        Optional<Node> parent = ifStmt.getParentNode();
        return parent.isPresent() && parent.get() instanceof IfStmt p && p.getElseStmt().map(e -> e == ifStmt).orElse(false);
    }

    private static int stringChainLength(IfStmt first) {
        String subject = null;
        int length = 0;
        Statement current = first;
        while (current instanceof IfStmt ifStmt) {
            String s = comparedString(ifStmt.getCondition());
            if (s == null || subject != null && !subject.equals(s)) break;
            subject = s;
            length++;
            current = ifStmt.getElseStmt().orElse(null);
        }
        return length;
    }

    /** The variable compared in x.equals("lit"), "lit".equals(x) or x.equalsIgnoreCase("lit"), else null. */
    private static String comparedString(Expression cond) {
        if (!(cond instanceof MethodCallExpr call)) return null;
        if (!call.getNameAsString().startsWith("equals") || call.getArguments().size() != 1 || call.getScope().isEmpty()) {
            return null;
        }
        Expression scope = call.getScope().get();
        Expression arg = call.getArgument(0);
        if (arg.isStringLiteralExpr() && !scope.isStringLiteralExpr()) return scope.toString();
        if (scope.isStringLiteralExpr() && !arg.isStringLiteralExpr()) return arg.toString();
        return null;
    }

    private static boolean inLoop(Node node) {
        return node.findAncestor(ForStmt.class).isPresent() || node.findAncestor(ForEachStmt.class).isPresent()
                || node.findAncestor(WhileStmt.class).isPresent() || node.findAncestor(DoStmt.class).isPresent();
    }
}
