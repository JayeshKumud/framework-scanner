package com.frameworkscanner.rules;

import com.frameworkscanner.analysis.Describer;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.Severity;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Java naming conventions, and names that say what a method does. */
final class NamingRules implements RuleSet {

    static final String CLASS = "CLASS_NAMING";
    static final String METHOD = "METHOD_NAMING";
    static final String CONSTANT = "CONSTANT_NAMING";
    static final String VARIABLE = "VARIABLE_NAMING";
    static final String PACKAGE = "PACKAGE_NAMING";
    static final String NON_DESCRIPTIVE = "NON_DESCRIPTIVE_NAME";
    static final String VERB = "METHOD_NAME_VERB";

    private static final Pattern PASCAL = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private static final Pattern CAMEL = Pattern.compile("[a-z][A-Za-z0-9]*");
    private static final Pattern UPPER = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");
    private static final Pattern PACKAGE_NAME = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Pattern MEANINGLESS = Pattern.compile(
            "(?i)(test|method|func|function|temp|tmp|foo|bar|baz|xyz|abc|demo|sample|my|new|data|util|helper|do|doit|"
                    + "action|process|handle|run|check)\\d*|[a-z]{1,2}\\d*|.*\\d+$");
    private static final Set<String> OBJECT_METHODS = Set.of("toString", "equals", "hashCode", "main", "compareTo",
            "close", "run", "call", "apply", "accept", "test", "get", "iterator", "clone", "builder", "build", "of",
            "values", "valueOf");

    @Override
    public List<RuleInfo> rules() {
        return List.of(
                new RuleInfo(CLASS, "Class names in PascalCase", "Naming", Severity.LOW,
                        "Java classes are named in PascalCase (LoginPage, UserApiClient). Other styles make classes "
                                + "look like variables or constants and make the code base harder to search.",
                        "public class login_page { }\npublic class userapi { }",
                        "public class LoginPage { }\npublic class UserApi { }"),
                new RuleInfo(METHOD, "Method names in camelCase", "Naming", Severity.LOW,
                        "Methods are named in camelCase, starting lower case (clickLogin). Underscores, upper-case "
                                + "starts and all-caps names hide which methods exist when people search for one to reuse.",
                        "public void Click_Login() { ... }\npublic void ENTERUSER(String u) { ... }",
                        "public void clickLogin() { ... }\npublic void enterUsername(String username) { ... }"),
                new RuleInfo(CONSTANT, "Constants in UPPER_SNAKE_CASE", "Naming", Severity.LOW,
                        "static final values are constants and are named in UPPER_SNAKE_CASE, so a reader sees at once "
                                + "that the value never changes and can be shared.",
                        "private static final String loginUrl = \"/login\";\nstatic final int timeout = 30;",
                        "private static final String LOGIN_URL = \"/login\";\nstatic final int TIMEOUT_SECONDS = 30;"),
                new RuleInfo(VARIABLE, "Fields, parameters and variables in camelCase", "Naming", Severity.LOW,
                        "Fields, parameters and local variables are named in camelCase. Underscores and upper-case "
                                + "names look like constants or classes.",
                        "String User_Name = \"ada\";\nLocator Submit_Btn = page.locator(\"#submit\");",
                        "String userName = \"ada\";\nLocator submitButton = page.locator(\"#submit\");"),
                new RuleInfo(PACKAGE, "Package names in lower case", "Naming", Severity.LOW,
                        "Package names are all lower case (com.company.pages). Mixed case breaks on case-insensitive "
                                + "file systems (Windows, macOS) and differs from every library you use.",
                        "package com.company.Pages;\npackage com.company.API_Utils;",
                        "package com.company.pages;\npackage com.company.api.utils;"),
                new RuleInfo(NON_DESCRIPTIVE, "Names that say what the method does", "Naming", Severity.MEDIUM,
                        "Names such as test1, method2, doIt or temp say nothing about what the method does. Nobody "
                                + "finds them when looking for something to reuse, so they write a new one: this is "
                                + "how duplicate methods start.",
                        "public void method2() { /* logs in */ }\npublic String temp(String s) { /* trims, lower-cases */ }",
                        "public void logInAs(User user) { ... }\npublic String normaliseEmail(String email) { ... }"),
                new RuleInfo(VERB, "Method names start with a verb", "Naming", Severity.INFO,
                        "A method does something, so its name starts with a verb (clickSave, getTotal, isVisible). "
                                + "Consistent verbs (click, enter, select, get, verify) make methods easy to find "
                                + "with auto-complete.",
                        "public void loginButton() { ... }\npublic void userDetails(String id) { ... }",
                        "public void clickLoginButton() { ... }\npublic UserDetails fetchUserDetails(String id) { ... }"));
    }

    @Override
    public void check(RuleContext ctx) {
        Set<String> packagesSeen = new HashSet<>();
        for (ClassInfo c : ctx.classes()) {
            if (!PASCAL.matcher(c.name).matches()) {
                ctx.report(CLASS, c, null, "Class '" + c.name + "' is not in PascalCase; rename to '"
                        + pascal(c.name) + "'.");
            }
            if (!c.packageName.isEmpty() && packagesSeen.add(c.packageName)
                    && !PACKAGE_NAME.matcher(c.packageName).matches()) {
                ctx.report(PACKAGE, c, c.unit.getPackageDeclaration().orElse(null),
                        "Package '" + c.packageName + "' is not all lower case.");
            }
            checkFields(ctx, c);
            for (MethodInfo m : c.methods) checkMethod(ctx, c, m);
        }
    }

    private void checkFields(RuleContext ctx, ClassInfo c) {
        boolean constantsOnly = c.kind.equals("interface");
        for (FieldDeclaration f : c.node.getFields()) {
            for (VariableDeclarator v : f.getVariables()) {
                String name = v.getNameAsString();
                boolean constant = (f.isStatic() && f.isFinal() || constantsOnly) && isValueType(v.getTypeAsString());
                if (name.equals("serialVersionUID") || name.equalsIgnoreCase("log") || name.equalsIgnoreCase("logger")) {
                    continue;
                }
                if (constant && !UPPER.matcher(name).matches()) {
                    ctx.report(CONSTANT, c, v, "Constant '" + name + "' should be '" + upperSnake(name) + "'.");
                } else if (!constant && !(f.isStatic() && f.isFinal()) && !CAMEL.matcher(name).matches()) {
                    ctx.report(VARIABLE, c, v, "Field '" + name + "' should be '" + camel(name) + "'.");
                }
            }
        }
    }

    private void checkMethod(RuleContext ctx, ClassInfo c, MethodInfo m) {
        if (!CAMEL.matcher(m.name).matches()) {
            ctx.report(METHOD, m, null, "Method '" + m.name + "' should be '" + camel(m.name) + "'.");
        }
        boolean override = m.annotations.contains("Override");
        boolean meaningless = !override && !m.isStep() && MEANINGLESS.matcher(m.name).matches()
                && !OBJECT_METHODS.contains(m.name) && !m.annotations.contains("Test");
        if (meaningless) {
            ctx.report(NON_DESCRIPTIVE, m, null, "'" + m.name + "' does not say what the method does. "
                    + "Describe it: " + m.description);
        }
        if (!meaningless && !override && !m.isStep() && !m.annotations.contains("Test") && !OBJECT_METHODS.contains(m.name)
                && !c.kind.equals("record")) {
            List<String> words = Describer.splitWords(m.name);
            String first = words.isEmpty() ? "" : words.get(0).toLowerCase(Locale.ROOT);
            if (!first.isEmpty() && !Describer.KNOWN_VERBS.contains(first) && !COMMON_VERBS.contains(first)) {
                ctx.report(VERB, m, null, "'" + m.name + "' does not start with a verb.");
            }
        }
        for (Parameter p : m.node.getParameters()) {
            if (!CAMEL.matcher(p.getNameAsString()).matches()) {
                ctx.report(VARIABLE, m, p, "Parameter '" + p.getNameAsString() + "' should be '"
                        + camel(p.getNameAsString()) + "'.");
            }
        }
        for (VariableDeclarationExpr decl : m.node.findAll(VariableDeclarationExpr.class)) {
            for (VariableDeclarator v : decl.getVariables()) {
                String name = v.getNameAsString();
                if (!CAMEL.matcher(name).matches() && !(decl.isFinal() && UPPER.matcher(name).matches())) {
                    ctx.report(VARIABLE, m, v, "Variable '" + name + "' should be '" + camel(name) + "'.");
                }
            }
        }
    }

    /** Verbs not in the describer's list that are still fine at the start of a method name. */
    private static final Set<String> COMMON_VERBS = Set.of("refresh", "reset", "submit", "confirm", "cancel",
            "expand", "collapse", "toggle", "drag", "drop", "press", "release", "show", "hide", "enable", "disable",
            "attach", "detach", "connect", "disconnect", "register", "unregister", "sort", "filter", "map", "merge",
            "split", "join", "count", "sum", "mark", "move", "copy", "rename", "replace", "insert", "append",
            "prepare", "ensure", "assume", "expect", "list", "retry", "invoke", "trigger", "handle",
            "process", "with", "without", "sign", "reload", "visit", "close", "quit", "launch", "use", "match",
            "contains", "equals", "matches", "on", "verify", "skip", "keep", "resolve", "encode", "decode",
            "encrypt", "decrypt", "serialize", "deserialize", "write", "delete", "patch", "request", "receive",
            "browse", "pick", "type", "double", "right", "mouse", "key", "fetch", "query", "lookup", "populate",
            "edit", "modify", "change", "make", "new", "from", "as", "compute", "transform", "wrap", "unwrap", "assign");

    private static boolean isValueType(String type) {
        return Set.of("String", "int", "long", "double", "float", "boolean", "char", "byte", "short", "Integer",
                "Long", "Double", "Boolean", "Duration").contains(type);
    }

    static String camel(String name) {
        List<String> words = Describer.splitWords(name);
        if (words.isEmpty()) return name;
        StringBuilder out = new StringBuilder(words.get(0).toLowerCase(Locale.ROOT));
        for (String w : words.subList(1, words.size())) {
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    static String pascal(String name) {
        String c = camel(name);
        return c.isEmpty() ? c : Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    static String upperSnake(String name) {
        return String.join("_", Describer.splitWords(name)).toUpperCase(Locale.ROOT);
    }
}
