package com.frameworkscanner.rules;

import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.Severity;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;

import java.util.List;
import java.util.Set;

/** Exceptions and logging. */
final class ErrorHandlingRules implements RuleSet {

    static final String EMPTY_CATCH = "EMPTY_CATCH";
    static final String BROAD_CATCH = "BROAD_CATCH_SWALLOWED";
    static final String PRINT_STACK_TRACE = "PRINT_STACK_TRACE";
    static final String SYSTEM_OUT = "SYSTEM_OUT";

    private static final Set<String> BROAD = Set.of("Exception", "Throwable", "RuntimeException", "Error");

    @Override
    public List<RuleInfo> rules() {
        return List.of(
                new RuleInfo(EMPTY_CATCH, "No empty catch blocks", "Error handling", Severity.HIGH,
                        "An empty catch hides the failure: the test goes on in a wrong state and fails later with "
                                + "a misleading error, or passes when it should fail.",
                        "try {\n    page.locator(\"#cookie-ok\").click();\n} catch (Exception e) {\n}",
                        "// only if the banner is really optional:\nLocator ok = page.locator(\"#cookie-ok\");\nif (ok.isVisible()) ok.click();"),
                new RuleInfo(BROAD_CATCH, "Do not swallow broad exceptions", "Error handling", Severity.MEDIUM,
                        "catch (Exception e) that only logs turns every problem (a typo, a timeout, a 500) into a "
                                + "passing step. Catch the specific exception you expect, or let it fail the test.",
                        "try {\n    api.createUser(user);\n} catch (Exception e) {\n    System.out.println(\"failed\");\n}",
                        "api.createUser(user);   // let it fail the step\n// or catch only what you expect:\ncatch (TimeoutError e) { ... }"),
                new RuleInfo(PRINT_STACK_TRACE, "Log exceptions instead of printStackTrace", "Logging", Severity.LOW,
                        "printStackTrace writes to stderr, outside the test report and without the scenario name; "
                                + "in parallel runs the traces of different tests mix.",
                        "catch (IOException e) {\n    e.printStackTrace();\n}",
                        "catch (IOException e) {\n    throw new UncheckedIOException(\"Cannot read \" + file, e);\n}"),
                new RuleInfo(SYSTEM_OUT, "Use a logger instead of System.out", "Logging", Severity.LOW,
                        "System.out cannot be filtered by level, has no time or thread, and does not reach the "
                                + "test report. A logger (SLF4J) or the report API (scenario.log) does.",
                        "System.out.println(\"Clicked login\");",
                        "private static final Logger LOG = LoggerFactory.getLogger(LoginPage.class);\n\nLOG.info(\"Clicked login\");"));
    }

    @Override
    public void check(RuleContext ctx) {
        for (MethodInfo m : ctx.methods()) {
            for (CatchClause c : m.node.findAll(CatchClause.class)) {
                String type = c.getParameter().getTypeAsString();
                boolean empty = c.getBody().getStatements().isEmpty();
                if (empty) {
                    ctx.report(EMPTY_CATCH, m, c, "catch (" + type + ") is empty; the error is lost.");
                } else if (BROAD.contains(type) && c.getBody().findAll(ThrowStmt.class).isEmpty()
                        && c.getBody().findAll(MethodCallExpr.class).stream().noneMatch(x -> x.getNameAsString().equals("fail"))) {
                    ctx.report(BROAD_CATCH, m, c, "catch (" + type + ") swallows every error without rethrowing.");
                }
            }
            for (MethodCallExpr call : m.node.findAll(MethodCallExpr.class)) {
                String name = call.getNameAsString();
                String scope = call.getScope().map(Object::toString).orElse("");
                if (name.equals("printStackTrace") && call.getArguments().isEmpty()) {
                    ctx.report(PRINT_STACK_TRACE, m, call, scope + ".printStackTrace() bypasses the logs and the report.");
                }
                if ((scope.equals("System.out") || scope.equals("System.err"))
                        && Set.of("println", "print", "printf", "format").contains(name)) {
                    ctx.report(SYSTEM_OUT, m, call, scope + "." + name + "(...) instead of a logger.");
                }
            }
        }
    }
}
