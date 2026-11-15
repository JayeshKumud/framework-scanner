package com.frameworkscanner.model;

/** How important a finding is, most important first. */
public enum Severity {
    /** Causes flaky or wrong tests, or breaks parallel runs. */
    HIGH,
    /** Makes the code harder to reuse or maintain. */
    MEDIUM,
    /** Style and readability. */
    LOW,
    /** A suggestion. */
    INFO
}
