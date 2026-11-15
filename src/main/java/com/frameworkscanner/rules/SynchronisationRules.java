package com.frameworkscanner.rules;

import com.frameworkscanner.model.Action;
import com.frameworkscanner.model.ActionType;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.Severity;

import java.util.List;
import java.util.Map;

/** Waiting the right way with Playwright, and Playwright APIs that are deprecated or discouraged. */
final class SynchronisationRules implements RuleSet {

    static final String HARD_WAIT = "HARD_WAIT";
    static final String DEPRECATED = "PLAYWRIGHT_DEPRECATED_API";
    static final String PAGE_SELECTOR = "PLAYWRIGHT_PAGE_SELECTOR_METHOD";

    private static final Map<String, String> DEPRECATED_CALLS = Map.of(
            "waitForNavigation", "click (or the action), then page.waitForURL(...) or assertThat(page).hasURL(...)",
            "type", "locator.fill(text), or locator.pressSequentially(text) when each key press matters",
            "waitForSelector", "locator.waitFor() or an assertThat(locator) web-first assertion");

    @Override
    public List<RuleInfo> rules() {
        return List.of(
                new RuleInfo(HARD_WAIT, "No fixed waits (Thread.sleep, waitForTimeout)", "Synchronisation", Severity.HIGH,
                        "A fixed wait is either too short (the test fails when the app is slow: flaky) or too long "
                                + "(every run is slower). Playwright already waits for elements before acting, and "
                                + "web-first assertions retry until the condition holds.",
                        "page.locator(\"#save\").click();\nThread.sleep(5000);\nString msg = page.locator(\".toast\").textContent();",
                        "page.locator(\"#save\").click();\nassertThat(page.locator(\".toast\")).hasText(\"Saved\"); // retries up to the timeout"),
                new RuleInfo(DEPRECATED, "No deprecated Playwright APIs", "Playwright", Severity.MEDIUM,
                        "Deprecated methods (waitForNavigation, type, waitForSelector) are racy or will be removed. "
                                + "Their replacements wait for the right condition.",
                        "page.waitForNavigation(() -> page.click(\"#next\"));\npage.locator(\"#q\").type(\"shoes\");",
                        "page.locator(\"#next\").click();\npage.waitForURL(\"**/step-2\");\npage.locator(\"#q\").fill(\"shoes\");"),
                new RuleInfo(PAGE_SELECTOR, "Use locators instead of page.click(selector)", "Playwright", Severity.LOW,
                        "page.click(selector), page.fill(selector, ...) and similar are discouraged: a Locator is "
                                + "defined once (in the page object), re-resolved on every use and works with "
                                + "web-first assertions.",
                        "page.fill(\"#username\", user);\npage.click(\"#login\");",
                        "private final Locator username = page.locator(\"#username\");\nprivate final Locator login = page.locator(\"#login\");\n\nusername.fill(user);\nlogin.click();"));
    }

    @Override
    public void check(RuleContext ctx) {
        for (MethodInfo m : ctx.methods()) {
            for (Action a : m.actions) {
                if (a.type() == ActionType.HARD_WAIT) {
                    ctx.report(HARD_WAIT, m, a.line(), (a.verb().equals("sleep") ? "Thread.sleep" : "waitForTimeout")
                            + "(" + (a.hasKnownTarget() ? a.target() : "...") + ") waits a fixed time. Wait for a "
                            + "condition instead (locator.waitFor, assertThat(...), page.waitForURL).");
                }
                String replacement = DEPRECATED_CALLS.get(a.verb());
                if (replacement != null && a.type().name().startsWith("UI_")) {
                    ctx.report(DEPRECATED, m, a.line(), a.verb() + "(...) is deprecated. Use " + replacement + ".");
                }
                if (a.selectorArg()) {
                    ctx.report(PAGE_SELECTOR, m, a.line(), "page." + a.verb() + "(\"" + a.target() + "\""
                            + (a.type() == ActionType.UI_INPUT ? ", ..." : "") + ") takes a selector; define a Locator and call "
                            + a.verb() + "() on it.");
                }
            }
        }
    }
}
