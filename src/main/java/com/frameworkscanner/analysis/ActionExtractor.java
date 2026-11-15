package com.frameworkscanner.analysis;

import com.frameworkscanner.model.Action;
import com.frameworkscanner.model.ActionType;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.parse.ProjectParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds what a method does: the Playwright actions on elements, REST Assured requests and checks, assertions,
 * waits and calls to other project methods, in source order. Selectors and URLs are resolved from string
 * literals, local variables, fields of the class and constants of other classes ({@code Locators.LOGIN}), so
 * {@code loginButton.click()} is reported as a click on {@code #login}.
 * <p>
 * This is static analysis without compiling the framework: types are guessed from names and call shapes, so
 * a value only known at run time (a parameter) becomes {@link Action#UNKNOWN}.
 */
public final class ActionExtractor {

    static final Set<String> LOCATOR_FACTORIES = Set.of("locator", "getByRole", "getByText", "getByLabel",
            "getByPlaceholder", "getByTestId", "getByAltText", "getByTitle", "frameLocator");
    static final Set<String> LOCATOR_PASSTHROUGH = Set.of("first", "last", "nth", "filter", "and", "or",
            "contentFrame", "owner");
    static final Set<String> CLICK = Set.of("click", "dblclick", "check", "uncheck", "hover", "press", "tap",
            "focus", "dragTo", "setChecked", "dispatchEvent", "scrollIntoViewIfNeeded");
    static final Set<String> INPUT = Set.of("fill", "type", "pressSequentially", "selectOption", "setInputFiles",
            "clear", "selectText");
    static final Set<String> READ = Set.of("textContent", "innerText", "innerHTML", "inputValue", "getAttribute",
            "isVisible", "isHidden", "isEnabled", "isDisabled", "isChecked", "isEditable", "count",
            "allTextContents", "allInnerTexts", "boundingBox");
    static final Set<String> PAGE_READ = Set.of("title", "url", "content");
    static final Set<String> WAIT = Set.of("waitFor", "waitForSelector", "waitForLoadState", "waitForURL",
            "waitForResponse", "waitForRequest", "waitForNavigation", "waitForPopup", "waitForCondition",
            "waitForFunction", "waitForEvent");
    static final Set<String> NAVIGATE = Set.of("navigate", "goBack", "goForward", "reload");
    static final Set<String> HTTP = Set.of("get", "post", "put", "delete", "patch", "head", "options");
    static final Set<String> REST_ROOTS = Set.of("given", "with", "when", "RestAssured");
    static final Set<String> REST_SETUP = Set.of("baseUri", "basePath", "port", "header", "headers", "contentType",
            "accept", "body", "queryParam", "queryParams", "pathParam", "pathParams", "formParam", "formParams",
            "multiPart", "auth", "basic", "oauth2", "preemptive", "cookie", "cookies", "spec", "relaxedHTTPSValidation",
            "param", "params");
    static final Set<String> REST_CHECKS = Set.of("statusCode", "body", "contentType", "header", "headers", "time",
            "statusLine", "cookie", "spec", "assertThat");
    static final Set<String> ASSERTS = Set.of("assertEquals", "assertNotEquals", "assertTrue", "assertFalse",
            "assertNull", "assertNotNull", "assertSame", "assertNotSame", "assertThrows", "assertAll", "fail",
            "assertArrayEquals", "assertIterableEquals", "assertLinesMatch", "assertDoesNotThrow");
    static final Set<String> REST_TYPES = Set.of("Response", "ValidatableResponse", "RequestSpecification",
            "ExtractableResponse", "RequestSender");

    private final ClassInfo owner;
    private final Map<String, String> constants;
    private final Set<String> projectMethods;
    private final Map<String, String> locals = new HashMap<>();
    private final Set<String> localLocators = new HashSet<>();
    /** Variables and fields that hold a REST Assured request or response. */
    private final Set<String> restVars = new HashSet<>();

    /**
     * Creates an extractor for the methods of one class.
     *
     * @param owner          the class (for field values)
     * @param constants      string constants of every class, by {@code ClassName.FIELD}
     * @param projectMethods names of every library method, to recognise calls to them
     */
    public ActionExtractor(ClassInfo owner, Map<String, String> constants, Set<String> projectMethods) {
        this.owner = owner;
        this.constants = constants;
        this.projectMethods = projectMethods;
    }

    /**
     * Resolves a locator expression without method context (for field initialisers).
     *
     * @param expr  a locator call
     * @param owner its class
     * @return the selector description, or null
     */
    public static String describeStatic(Expression expr, ClassInfo owner) {
        String t = new ActionExtractor(owner, Map.of(), Set.of()).targetOf(expr);
        return Action.UNKNOWN.equals(t) ? null : t;
    }

    /**
     * Tells whether an expression creates a Playwright locator, {@code page.locator("#x")} or {@code getByRole(...)}.
     *
     * @param expr any expression
     * @return true for locator factory calls (also chained: {@code .first()}, {@code .nth(2)})
     */
    public static boolean isLocatorFactoryCall(Expression expr) {
        if (!(expr instanceof MethodCallExpr call)) return false;
        String name = call.getNameAsString();
        if (LOCATOR_FACTORIES.contains(name)) return true;
        return LOCATOR_PASSTHROUGH.contains(name) && call.getScope().map(ActionExtractor::isLocatorFactoryCall)
                .orElse(false);
    }

    /**
     * The actions of one method, in source order.
     *
     * @param method the method
     * @return its actions
     */
    public List<Action> extract(MethodDeclaration method) {
        locals.clear();
        localLocators.clear();
        restVars.clear();
        if (owner.node != null) {
            owner.node.getFields().forEach(f -> f.getVariables().forEach(v -> {
                if (REST_TYPES.contains(simpleType(v.getTypeAsString()))) restVars.add(v.getNameAsString());
            }));
        }
        for (Parameter p : method.getParameters()) {
            if (ProjectParser.isLocatorType(p.getTypeAsString())) localLocators.add(p.getNameAsString());
            if (REST_TYPES.contains(simpleType(p.getTypeAsString()))) restVars.add(p.getNameAsString());
        }
        for (VariableDeclarator v : method.findAll(VariableDeclarator.class)) {
            String name = v.getNameAsString();
            if (ProjectParser.isLocatorType(v.getTypeAsString())) localLocators.add(name);
            if (REST_TYPES.contains(simpleType(v.getTypeAsString()))) restVars.add(name);
            v.getInitializer().ifPresent(init -> {
                if (isRestChain(init)) restVars.add(name);
                if (isLocatorExpr(init)) localLocators.add(name);
                String t = targetOf(init);
                if (!Action.UNKNOWN.equals(t)) locals.put(name, t);
            });
        }
        List<MethodCallExpr> calls = new ArrayList<>(method.findAll(MethodCallExpr.class));
        // chained calls share a start, so order by end: page.locator("#a").click() -> locator before click
        calls.sort(Comparator.comparing((MethodCallExpr c) -> c.getEnd().map(p -> p.line).orElse(0))
                .thenComparing(c -> c.getEnd().map(p -> p.column).orElse(0)));
        List<Action> actions = new ArrayList<>();
        for (MethodCallExpr call : calls) {
            Action a = classify(call, method);
            if (a != null) actions.add(a);
        }
        return actions;
    }

    private Action classify(MethodCallExpr call, MethodDeclaration method) {
        String name = call.getNameAsString();
        int line = call.getBegin().map(p -> p.line).orElse(0);
        Optional<Expression> scope = call.getScope();
        String scopeText = scope.map(Node::toString).orElse("");

        // fixed waits
        if (name.equals("sleep") && (scopeText.equals("Thread") || scopeText.startsWith("TimeUnit."))) {
            return new Action(ActionType.HARD_WAIT, name, argTarget(call, 0), line);
        }
        if (name.equals("waitForTimeout")) return new Action(ActionType.HARD_WAIT, name, argTarget(call, 0), line);

        // assertions: Playwright/AssertJ assertThat(x).isVisible(), TestNG/JUnit Assert.assertEquals(...)
        Optional<MethodCallExpr> assertThat = scope.filter(Expression::isMethodCallExpr).map(Expression::asMethodCallExpr)
                .filter(s -> s.getNameAsString().equals("assertThat") && !s.getScope().map(this::isRestChain).orElse(false));
        if (assertThat.isPresent()) {
            MethodCallExpr at = assertThat.get();
            String target = at.getArguments().isEmpty() ? Action.UNKNOWN : targetOf(at.getArgument(0));
            return new Action(ActionType.ASSERT, name, target, line);
        }
        if (name.equals("assertThat") && call.getArguments().size() >= 2) { // Hamcrest assertThat(actual, matcher)
            return new Action(ActionType.ASSERT, name, Action.UNKNOWN, line);
        }
        if (ASSERTS.contains(name) && (scope.isEmpty() || scopeText.endsWith("Assert") || scopeText.endsWith("Assertions")
                || scopeText.equals("softAssert") || scopeText.endsWith("SoftAssert"))) {
            return new Action(ActionType.ASSERT, name, Action.UNKNOWN, line);
        }

        // REST Assured
        if (scope.isPresent() && isRestChain(scope.get()) || scopeText.equals("RestAssured")) {
            if (HTTP.contains(name)) {
                return new Action(ActionType.API_REQUEST, name.toUpperCase(Locale.ROOT), argTarget(call, 0), line);
            }
            boolean afterThen = chainContains(call, "then");
            if (afterThen && REST_CHECKS.contains(name)) {
                return new Action(ActionType.ASSERT, name, argTarget(call, 0), line);
            }
            if (!afterThen && REST_SETUP.contains(name)) {
                return new Action(ActionType.API_SETUP, name, argTarget(call, 0), line);
            }
            if (name.equals("then") || name.equals("given") || name.equals("when") || name.equals("and")
                    || name.equals("extract") || name.equals("response") || name.equals("log")) {
                return null;
            }
        }

        // Playwright
        boolean onLocator = scope.map(this::isLocatorExpr).orElse(false);
        if (NAVIGATE.contains(name) && (name.equals("navigate") || looksLikePage(scopeText))) {
            return new Action(ActionType.UI_NAVIGATE, name, name.equals("navigate") ? argTarget(call, 0) : scopeText, line);
        }
        ActionType ui = CLICK.contains(name) ? ActionType.UI_CLICK : INPUT.contains(name) ? ActionType.UI_INPUT
                : READ.contains(name) ? ActionType.UI_READ : WAIT.contains(name) ? ActionType.UI_WAIT : null;
        if (ui != null) {
            if (onLocator) return new Action(ui, name, targetOf(scope.get()), line);
            if (looksLikePage(scopeText) || scope.isEmpty() && !projectMethods.contains(name)) {
                boolean selectorForm = !call.getArguments().isEmpty() && ui != ActionType.UI_WAIT
                        && !(name.equals("press") && call.getArguments().size() == 1);
                String target = name.equals("waitForLoadState") ? "page"
                        : call.getArguments().isEmpty() ? "page" : argTarget(call, 0);
                return new Action(ui, name, target, line, selectorForm);
            }
        }
        if (PAGE_READ.contains(name) && looksLikePage(scopeText) && call.getArguments().isEmpty()) {
            return new Action(ActionType.UI_READ, name, "page", line);
        }

        // calls to other methods of the project
        if (projectMethods.contains(name) && !LOCATOR_FACTORIES.contains(name)
                && !name.equals(method.getNameAsString())) {
            return new Action(ActionType.CALL, name, name, line);
        }
        return null;
    }

    /** True if the expression is a REST Assured chain: given()..., RestAssured.given()..., when()... */
    private boolean isRestChain(Expression expr) {
        Expression e = expr;
        while (true) {
            if (e instanceof MethodCallExpr m) {
                if (m.getScope().isEmpty()) return REST_ROOTS.contains(m.getNameAsString());
                e = m.getScope().get();
            } else if (e instanceof NameExpr n) {
                return n.getNameAsString().equals("RestAssured") || restVars.contains(n.getNameAsString());
            } else if (e instanceof FieldAccessExpr f) {
                return f.getNameAsString().equals("RestAssured")
                        || f.getScope().isThisExpr() && restVars.contains(f.getNameAsString());
            } else {
                return false;
            }
        }
    }

    private static String simpleType(String type) {
        String t = type.replaceAll("<.*>", "");
        return t.substring(t.lastIndexOf('.') + 1);
    }

    private static boolean chainContains(MethodCallExpr call, String name) {
        Optional<Expression> e = call.getScope();
        while (e.isPresent() && e.get() instanceof MethodCallExpr m) {
            if (m.getNameAsString().equals(name)) return true;
            e = m.getScope();
        }
        return false;
    }

    private static boolean looksLikePage(String scope) {
        String s = scope.toLowerCase(Locale.ROOT);
        return s.equals("page") || s.endsWith(".page") || s.endsWith("page()") || s.equals("this.page")
                || s.endsWith("frame") || s.contains("getpage()") || s.equals("driver");
    }

    private boolean isLocatorExpr(Expression expr) {
        if (isLocatorFactoryCall(expr)) return true;
        if (expr instanceof NameExpr n) {
            String name = n.getNameAsString();
            return localLocators.contains(name) || owner.locatorFields.contains(name);
        }
        if (expr instanceof FieldAccessExpr f && f.getScope().isThisExpr()) {
            return owner.locatorFields.contains(f.getNameAsString());
        }
        if (expr instanceof MethodCallExpr m && LOCATOR_PASSTHROUGH.contains(m.getNameAsString())) {
            return m.getScope().map(this::isLocatorExpr).orElse(false);
        }
        return expr instanceof EnclosedExpr en && isLocatorExpr(en.getInner());
    }

    private String argTarget(MethodCallExpr call, int index) {
        return call.getArguments().size() > index ? targetOf(call.getArgument(index)) : Action.UNKNOWN;
    }

    /**
     * Resolves what an expression stands for: a selector, URL or other literal value.
     *
     * @param expr the expression
     * @return the value, or {@link Action#UNKNOWN}
     */
    String targetOf(Expression expr) {
        if (expr instanceof StringLiteralExpr s) return s.asString();
        if (expr instanceof TextBlockLiteralExpr t) return t.asString().strip();
        if (expr instanceof CharLiteralExpr c) return c.asChar() + "";
        if (expr instanceof LiteralStringValueExpr l) return l.getValue();
        if (expr instanceof EnclosedExpr e) return targetOf(e.getInner());
        if (expr instanceof CastExpr c) return targetOf(c.getExpression());
        if (expr instanceof NameExpr n) {
            String name = n.getNameAsString();
            String v = locals.get(name);
            if (v == null) v = owner.fieldValues.get(name);
            if (v == null) v = constants.get(owner.name + "." + name);
            return v == null ? Action.UNKNOWN : v;
        }
        if (expr instanceof FieldAccessExpr f) {
            if (f.getScope().isThisExpr()) return owner.fieldValues.getOrDefault(f.getNameAsString(), Action.UNKNOWN);
            String scope = f.getScope().toString();
            String simple = scope.substring(scope.lastIndexOf('.') + 1);
            return constants.getOrDefault(simple + "." + f.getNameAsString(), Action.UNKNOWN);
        }
        if (expr instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            String l = targetOf(b.getLeft());
            String r = targetOf(b.getRight());
            if (Action.UNKNOWN.equals(l) && Action.UNKNOWN.equals(r)) return Action.UNKNOWN;
            return (Action.UNKNOWN.equals(l) ? "{}" : l) + (Action.UNKNOWN.equals(r) ? "{}" : r);
        }
        if (expr instanceof MethodCallExpr m) return targetOfCall(m);
        return Action.UNKNOWN;
    }

    private String targetOfCall(MethodCallExpr m) {
        String name = m.getNameAsString();
        if (name.equals("format") && m.getScope().map(s -> s.toString().equals("String")).orElse(false)
                && !m.getArguments().isEmpty()) {
            String f = targetOf(m.getArgument(0));
            return Action.UNKNOWN.equals(f) ? f : f.replaceAll("%[-0-9.]*[sdf]", "{}");
        }
        if (LOCATOR_PASSTHROUGH.contains(name)) {
            return m.getScope().map(this::targetOf).orElse(Action.UNKNOWN);
        }
        if (!LOCATOR_FACTORIES.contains(name)) return Action.UNKNOWN;
        String own = switch (name) {
            case "getByRole" -> role(m);
            case "getByText" -> "text=" + argTarget(m, 0);
            case "getByLabel" -> "label=" + argTarget(m, 0);
            case "getByPlaceholder" -> "placeholder=" + argTarget(m, 0);
            case "getByTestId" -> "testid=" + argTarget(m, 0);
            case "getByAltText" -> "alt=" + argTarget(m, 0);
            case "getByTitle" -> "title=" + argTarget(m, 0);
            default -> argTarget(m, 0);
        };
        Optional<Expression> scope = m.getScope();
        if (scope.isPresent() && isLocatorExpr(scope.get())) {
            String parent = targetOf(scope.get());
            return parent + " >> " + own;
        }
        return own;
    }

    private String role(MethodCallExpr m) {
        if (m.getArguments().isEmpty()) return "role=?";
        String arg = m.getArgument(0).toString();
        String role = arg.substring(arg.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String name = null;
        if (m.getArguments().size() > 1) {
            for (MethodCallExpr opt : m.getArgument(1).findAll(MethodCallExpr.class)) {
                if (opt.getNameAsString().equals("setName")) name = argTarget(opt, 0);
            }
        }
        return "role=" + role + (name == null ? "" : "[name=" + name + "]");
    }
}
