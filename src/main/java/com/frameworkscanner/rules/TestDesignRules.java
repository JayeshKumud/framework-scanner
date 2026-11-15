package com.frameworkscanner.rules;

import com.frameworkscanner.model.Action;
import com.frameworkscanner.model.ActionType;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Page objects, step definitions and tests: where locators live and that checks really check. */
final class TestDesignRules implements RuleSet {

    static final String LOCATOR_IN_STEP = "LOCATOR_IN_STEP_DEFINITION";
    static final String DUPLICATE_LOCATOR = "DUPLICATE_LOCATOR";
    static final String THEN_WITHOUT_ASSERT = "THEN_STEP_WITHOUT_ASSERTION";
    static final String TEST_WITHOUT_ASSERT = "TEST_WITHOUT_ASSERTION";
    static final String BRITTLE_XPATH = "BRITTLE_XPATH";
    static final String UNUSED_STEP = "UNUSED_STEP_DEFINITION";

    private static final Pattern POSITIONAL = Pattern.compile("\\[\\d+]");

    @Override
    public List<RuleInfo> rules() {
        return List.of(
                new RuleInfo(LOCATOR_IN_STEP, "No locators in step definitions", "Test design", Severity.MEDIUM,
                        "Step definitions describe behaviour; page objects know the page. Locators in steps are "
                                + "copied to every step that touches the element, and a changed id must be fixed in "
                                + "many places.",
                        "@When(\"I log in as {string}\")\npublic void logIn(String user) {\n    page.locator(\"#user\").fill(user);\n    page.locator(\"#login\").click();\n}",
                        "@When(\"I log in as {string}\")\npublic void logIn(String user) {\n    loginPage.logInAs(user);   // locators live in LoginPage\n}"),
                new RuleInfo(DUPLICATE_LOCATOR, "One locator, one place", "Test design", Severity.MEDIUM,
                        "The same selector defined in several classes is a duplicate in disguise: when the page "
                                + "changes, some copies get fixed and others do not. Define it once in the page "
                                + "object (or a locator file) and reuse it.",
                        "// LoginPage\npage.locator(\"#login-btn\").click();\n// HeaderSteps\npage.locator(\"#login-btn\").click();",
                        "// LoginPage\npublic Locator loginButton() { return page.locator(\"#login-btn\"); }\n// HeaderSteps\nloginPage.loginButton().click();"),
                new RuleInfo(THEN_WITHOUT_ASSERT, "Then steps assert something", "Test design", Severity.MEDIUM,
                        "A Then step that does not assert passes whatever the application does, so the scenario "
                                + "can never fail on it.",
                        "@Then(\"the order is confirmed\")\npublic void orderConfirmed() {\n    confirmationPage.getMessage();\n}",
                        "@Then(\"the order is confirmed\")\npublic void orderConfirmed() {\n    assertThat(confirmationPage.message()).hasText(\"Thank you\");\n}"),
                new RuleInfo(TEST_WITHOUT_ASSERT, "Tests assert something", "Test design", Severity.MEDIUM,
                        "A @Test without any assertion only checks that nothing threw an exception.",
                        "@Test\npublic void searchWorks() {\n    searchPage.search(\"shoes\");\n}",
                        "@Test\npublic void searchFindsProducts() {\n    searchPage.search(\"shoes\");\n    assertThat(searchPage.results()).not().hasCount(0);\n}"),
                new RuleInfo(BRITTLE_XPATH, "No absolute or positional XPath", "Test design", Severity.MEDIUM,
                        "/html/body/div[2]/div[1]/button breaks with every layout change. Prefer user-facing "
                                + "locators (getByRole, getByLabel, getByTestId) or a stable id.",
                        "page.locator(\"/html/body/div[2]/div[1]/form/button[2]\").click();",
                        "page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Save\")).click();\npage.getByTestId(\"save-button\").click();"),
                new RuleInfo(UNUSED_STEP, "No unused step definitions", "Test design", Severity.LOW,
                        "A step definition that no feature file uses is dead code, and often an old copy of a step "
                                + "that is written differently now. Delete it or reuse it.",
                        "@When(\"user clicks on login button\")   // no feature uses this any more\npublic void clickLogin() { ... }",
                        "// deleted; features use: When I log in as \"ada\""));
    }

    @Override
    public void check(RuleContext ctx) {
        Map<String, TreeSet<String>> locatorClasses = new LinkedHashMap<>();
        Map<String, List<Object[]>> locatorPlaces = new LinkedHashMap<>();
        for (ClassInfo c : ctx.classes()) {
            boolean steps = c.methods.stream().anyMatch(MethodInfo::isStep);
            for (MethodInfo m : c.methods) {
                List<Action> ui = m.actions.stream().filter(a -> isElementAction(a)).toList();
                if (steps && !ui.isEmpty()) {
                    ctx.report(LOCATOR_IN_STEP, m, ui.get(0).line(), "Step '" + m.name + "' works on elements directly ("
                            + String.join(", ", ui.stream().map(a -> a.verb() + " " + a.target()).distinct().limit(3).toList())
                            + "); move this to a page object.");
                }
                for (Action a : ui) {
                    if (!a.hasKnownTarget()) continue;
                    String sel = a.target();
                    locatorClasses.computeIfAbsent(sel, k -> new TreeSet<>()).add(c.name);
                    locatorPlaces.computeIfAbsent(sel, k -> new ArrayList<>()).add(new Object[]{m, a.line()});
                    if (isBrittleXpath(sel)) {
                        ctx.report(BRITTLE_XPATH, m, a.line(), "\"" + sel + "\" depends on the page layout.");
                    }
                }
                if ("Then".equals(m.stepKeyword) && !asserts(m)) {
                    ctx.report(THEN_WITHOUT_ASSERT, m, null, "Then step \"" + m.stepText + "\" never asserts.");
                }
                if (m.annotations.contains("Test") && !asserts(m)) {
                    ctx.report(TEST_WITHOUT_ASSERT, m, null, "Test '" + m.name + "' never asserts.");
                }
                if (m.isStep() && m.stepUsageCount == 0) {
                    ctx.report(UNUSED_STEP, m, null, "No feature file uses \"" + m.stepText + "\".");
                }
            }
        }
        for (Map.Entry<String, TreeSet<String>> e : locatorClasses.entrySet()) {
            if (e.getValue().size() < 2) continue;
            java.util.Set<String> reported = new java.util.HashSet<>();
            for (Object[] place : locatorPlaces.get(e.getKey())) {
                MethodInfo m = (MethodInfo) place[0];
                if (!reported.add(m.className)) continue;
                ctx.report(DUPLICATE_LOCATOR, m, (int) place[1], "Selector \"" + e.getKey() + "\" is also used in "
                        + String.join(", ", e.getValue().stream().filter(n -> !n.equals(m.className)).toList()) + ".");
            }
        }
    }

    private static boolean isElementAction(Action a) {
        return (a.type() == ActionType.UI_CLICK || a.type() == ActionType.UI_INPUT || a.type() == ActionType.UI_READ
                || a.type() == ActionType.UI_WAIT && !a.verb().equals("waitForLoadState") && !a.verb().equals("waitForURL"))
                && !"page".equals(a.target());
    }

    private static boolean asserts(MethodInfo m) {
        return m.actions.stream().anyMatch(a -> a.type() == ActionType.ASSERT
                || a.type() == ActionType.CALL && a.target().toLowerCase(Locale.ROOT)
                .matches("(verify|assert|check|validate|should|expect|ensure).*"));
    }

    static boolean isBrittleXpath(String selector) {
        String s = selector.startsWith("xpath=") ? selector.substring(6) : selector;
        if (s.startsWith("/html") || s.startsWith("html/")) return true;
        return (s.startsWith("/") || s.startsWith("(")) && POSITIONAL.matcher(s).results().count() >= 2;
    }
}
