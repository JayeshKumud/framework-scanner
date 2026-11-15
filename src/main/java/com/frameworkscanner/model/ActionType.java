package com.frameworkscanner.model;

/** Kinds of {@link Action}. */
public enum ActionType {
    /** Opens a URL in the browser. */
    UI_NAVIGATE,
    /** Clicks, hovers, checks, presses a key. */
    UI_CLICK,
    /** Types or selects a value. */
    UI_INPUT,
    /** Reads text, a value, an attribute or a state from the page. */
    UI_READ,
    /** Waits for an element, a load state, a URL or a response. */
    UI_WAIT,
    /** Waits a fixed time (Thread.sleep, waitForTimeout). */
    HARD_WAIT,
    /** Sends an HTTP request (REST Assured). */
    API_REQUEST,
    /** Sets up a request: base URI, header, body, auth. */
    API_SETUP,
    /** Checks something: Playwright assertThat, REST Assured then(), TestNG/JUnit/AssertJ asserts. */
    ASSERT,
    /** Calls another method of the scanned project. */
    CALL
}
