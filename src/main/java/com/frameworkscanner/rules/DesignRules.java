package com.frameworkscanner.rules;

import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.Severity;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Size, structure and shared state. */
final class DesignRules implements RuleSet {

    static final String LONG_METHOD = "LONG_METHOD";
    static final String TOO_MANY_PARAMETERS = "TOO_MANY_PARAMETERS";
    static final String GOD_CLASS = "GOD_CLASS";
    static final String STATIC_STATE = "STATIC_MUTABLE_STATE";
    static final String UTILITY_CLASS = "UTILITY_CLASS_INSTANTIABLE";
    static final String HARDCODED_URL = "HARDCODED_URL";
    static final String REPEATED_LITERAL = "REPEATED_LITERAL";

    private static final Set<String> THREAD_UNSAFE = Set.of("Page", "Browser", "BrowserContext", "Playwright",
            "APIRequestContext", "Locator", "RequestSpecification", "Response", "ValidatableResponse", "WebDriver",
            "Map", "HashMap", "List", "ArrayList", "Set", "HashSet");
    private static final Pattern URL = Pattern.compile("(?i)^https?://\\S+");

    @Override
    public List<RuleInfo> rules() {
        return List.of(
                new RuleInfo(LONG_METHOD, "Short methods that do one thing", "Design", Severity.MEDIUM,
                        "A long method usually does several things (log in, search, check the result). Each part "
                                + "cannot be reused on its own, so people copy the part they need: duplicates. "
                                + "Split it into named steps.",
                        "public void placeOrder() {\n    // 80 lines: log in, search, add to cart, pay, check confirmation\n}",
                        "public void placeOrder(User user, Product product) {\n    loginPage.logInAs(user);\n    searchPage.search(product.name());\n    cartPage.add(product);\n    checkoutPage.pay(user.card());\n}"),
                new RuleInfo(TOO_MANY_PARAMETERS, "Few parameters: group them in an object", "Design", Severity.MEDIUM,
                        "With many parameters, callers mix up their order (two Strings swap without any error) and "
                                + "every new field changes every caller. A record or a builder names each value.",
                        "public void createUser(String first, String last, String email, String phone, String city, boolean admin) { ... }",
                        "public record NewUser(String first, String last, String email, String phone, String city, boolean admin) {}\n\npublic void createUser(NewUser user) { ... }\n// or: NewUser.builder().first(\"Ada\").email(\"ada@x.test\").build()"),
                new RuleInfo(GOD_CLASS, "Classes with one responsibility", "Design", Severity.MEDIUM,
                        "A class with very many methods (CommonUtils, BaseTest with everything) is a place where "
                                + "people add instead of search. Split it by topic: DateUtils, StringUtils, page "
                                + "objects per page.",
                        "public class CommonUtils { // 120 methods: dates, strings, files, waits, API calls\n}",
                        "public final class DateUtils { ... }\npublic final class FileUtils { ... }\npublic class CheckoutPage { ... }"),
                new RuleInfo(STATIC_STATE, "No static mutable Playwright/REST state", "Design", Severity.HIGH,
                        "A static non-final Page, Browser, RequestSpecification, Response or collection is shared "
                                + "by all tests. When tests run in parallel they overwrite each other's browser or "
                                + "response: random failures. Give each scenario its own instance (dependency "
                                + "injection such as PicoContainer, or ThreadLocal).",
                        "public class DriverFactory {\n    public static Page page;\n    public static Response lastResponse;\n}",
                        "public class TestContext {          // one per scenario (PicoContainer)\n    private final Page page;\n    private Response lastResponse;\n}\n// or: private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();"),
                new RuleInfo(UTILITY_CLASS, "Utility classes are final with a private constructor", "Java idioms", Severity.LOW,
                        "A class with only static methods is never meant to be created or extended. Make it final "
                                + "with a private constructor, so nobody writes new CommonUtils() or a subclass.",
                        "public class StringUtils {\n    public static String trim(String s) { ... }\n}",
                        "public final class StringUtils {\n    private StringUtils() {}\n    public static String trim(String s) { ... }\n}"),
                new RuleInfo(HARDCODED_URL, "No hard-coded URLs", "Configuration", Severity.MEDIUM,
                        "A URL in code ties the tests to one environment. Read base URLs from configuration "
                                + "(per environment) and keep only the path in code.",
                        "page.navigate(\"https://qa.myapp.com/login\");\ngiven().baseUri(\"https://qa-api.myapp.com\")",
                        "page.navigate(config.uiBaseUrl() + \"/login\");\ngiven().spec(apiSpec)   // base URI from config"),
                new RuleInfo(REPEATED_LITERAL, "Repeated strings as constants or enums", "Java idioms", Severity.LOW,
                        "The same string written many times (a status, a header name, a role) is easy to mistype "
                                + "and hard to change. Use a constant, or an enum when it is one of a fixed set of "
                                + "values.",
                        "if (status.equals(\"ACTIVE\")) ...\nuser.setStatus(\"ACTIVE\");\nassertEquals(\"ACTIVE\", body.status);",
                        "public enum Status { ACTIVE, INACTIVE }\n\nif (status == Status.ACTIVE) ...\nuser.setStatus(Status.ACTIVE);"));
    }

    @Override
    public void check(RuleContext ctx) {
        var s = ctx.settings();
        for (ClassInfo c : ctx.classes()) {
            if (c.methods.size() > s.godClassMethods) {
                ctx.report(GOD_CLASS, c, null, c.name + " has " + c.methods.size() + " methods (limit "
                        + s.godClassMethods + "). Split it by responsibility.");
            }
            for (FieldDeclaration f : c.node.getFields()) {
                if (!f.isStatic() || f.isFinal()) continue;
                String type = f.getElementType().asString().replaceAll("<.*>", "");
                String simple = type.substring(type.lastIndexOf('.') + 1);
                if (THREAD_UNSAFE.contains(simple)) {
                    for (VariableDeclarator v : f.getVariables()) {
                        ctx.report(STATIC_STATE, c, v, "static " + simple + " " + v.getNameAsString()
                                + " is shared by every test thread.");
                    }
                }
            }
            checkUtilityClass(ctx, c);
            checkLiterals(ctx, c);
        }
        for (MethodInfo m : ctx.methods()) {
            if (m.lines() > s.longMethodLines) {
                ctx.report(LONG_METHOD, m, null, m.name + " has " + m.lines() + " lines (limit " + s.longMethodLines + ").");
            }
            if (m.parameterTypes.size() > s.maxParameters && !m.isStep()) {
                ctx.report(TOO_MANY_PARAMETERS, m, null, m.name + " has " + m.parameterTypes.size()
                        + " parameters (limit " + s.maxParameters + "). Group them in a record or builder.");
            }
        }
    }

    private void checkUtilityClass(RuleContext ctx, ClassInfo c) {
        if (!(c.node instanceof ClassOrInterfaceDeclaration cls) || cls.isInterface() || cls.isAbstract()) return;
        if (c.methods.isEmpty() || !c.methods.stream().allMatch(m -> m.isStatic)) return;
        if (cls.getFields().stream().anyMatch(f -> !f.isStatic())) return;
        if (!cls.getExtendedTypes().isEmpty() || cls.getAnnotations().stream().map(AnnotationExpr::getNameAsString)
                .anyMatch(a -> a.equals("UtilityClass"))) {
            return;
        }
        boolean privateCtor = cls.getConstructors().stream().anyMatch(ConstructorDeclaration::isPrivate);
        if (!privateCtor || !cls.isFinal()) {
            ctx.report(UTILITY_CLASS, c, null, c.name + " has only static methods: make it final"
                    + (privateCtor ? "" : " and add a private constructor") + ".");
        }
    }

    private void checkLiterals(RuleContext ctx, ClassInfo c) {
        Map<String, List<StringLiteralExpr>> byValue = new LinkedHashMap<>();
        for (StringLiteralExpr lit : c.node.findAll(StringLiteralExpr.class)) {
            if (!isOwnNode(c, lit)) continue;
            String v = lit.asString();
            if (URL.matcher(v).matches()) {
                ctx.report(HARDCODED_URL, c, lit, "URL \"" + v + "\" is hard-coded; read the base URL from configuration.");
            }
            if (v.length() < 3 || v.isBlank() || inAnnotation(lit) || inConstantDeclaration(lit)) continue;
            byValue.computeIfAbsent(v, k -> new java.util.ArrayList<>()).add(lit);
        }
        for (Map.Entry<String, List<StringLiteralExpr>> e : byValue.entrySet()) {
            if (e.getValue().size() >= ctx.settings().repeatedLiteralCount) {
                ctx.report(REPEATED_LITERAL, c, e.getValue().get(0), "\"" + e.getKey() + "\" is written "
                        + e.getValue().size() + " times in " + c.name + "; make it a constant or enum value.");
            }
        }
    }

    /** Nested classes are checked as classes of their own. */
    private static boolean isOwnNode(ClassInfo c, Node node) {
        return node.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class).map(t -> t == c.node).orElse(false);
    }

    private static boolean inAnnotation(Node node) {
        return node.findAncestor(AnnotationExpr.class).isPresent();
    }

    private static boolean inConstantDeclaration(Node node) {
        return node.findAncestor(FieldDeclaration.class).map(f -> f.isStatic() && f.isFinal()).orElse(false);
    }
}
