package com.frameworkscanner.model;

/**
 * One thing a method does that matters for its purpose: a Playwright action on an element, an HTTP request with
 * REST Assured, an assertion, a wait, or a call to another project method. The actions of a method, in order,
 * are its "behaviour fingerprint" (used to find methods that do the same job) and the base of its generated
 * description.
 *
 * @param type        what kind of action
 * @param verb        the API method, for example click, fill, GET, statusCode
 * @param target      what it acts on: a selector, a URL or endpoint, a method name; "?" when not known statically
 * @param line        source line
 * @param selectorArg true for Playwright's page-level form that takes a selector, {@code page.click("#id")}
 */
public record Action(ActionType type, String verb, String target, int line, boolean selectorArg) {

    /** Target used when the value is only known at run time (a parameter, a computed string). */
    public static final String UNKNOWN = "?";

    /**
     * An action in the normal form.
     *
     * @param type   what kind of action
     * @param verb   the API method
     * @param target what it acts on
     * @param line   source line
     */
    public Action(ActionType type, String verb, String target, int line) {
        this(type, verb, target, line, false);
    }

    /** @return true if the target was resolved to a literal value */
    public boolean hasKnownTarget() {
        return target != null && !UNKNOWN.equals(target);
    }

    /**
     * The token compared between methods: type, verb and target, ignoring case and spaces in the target.
     * Typed values (what is filled in) are not part of it, so two logins with different users still match, and
     * {@code page.click("#a")} matches {@code page.locator("#a").click()}.
     *
     * @return for example "UI_INPUT:fill:#username"
     */
    public String fingerprint() {
        String t = target == null ? UNKNOWN : target.toLowerCase().replaceAll("\\s+", "");
        return type + ":" + verb.toLowerCase() + ":" + t;
    }
}
