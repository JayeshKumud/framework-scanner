package com.frameworkscanner.model;

/** How similar two duplicate methods are, strongest first. */
public enum CloneLevel {
    /** Same code, ignoring layout and comments. */
    EXACT("Exact copy", "Same code; only formatting or comments differ."),
    /** Same code structure; names of variables, parameters or fields and literal values differ. */
    RENAMED("Renamed copy", "Same code structure; variable, parameter or field names and literal values differ."),
    /** Mostly the same code, with some statements added, removed or changed. */
    NEAR("Near copy", "Mostly the same code; some statements were added, removed or changed."),
    /** Different code that performs the same UI or API actions on the same targets, in the same order. */
    SAME_JOB("Same job", "Different code, but the same UI/API actions on the same elements or endpoints, in the same order.");

    private final String label;
    private final String meaning;

    CloneLevel(String label, String meaning) {
        this.label = label;
        this.meaning = meaning;
    }

    /** @return the name shown in reports */
    public String label() {
        return label;
    }

    /** @return one sentence explaining the level */
    public String meaning() {
        return meaning;
    }
}
