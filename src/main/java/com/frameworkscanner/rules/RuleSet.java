package com.frameworkscanner.rules;

import com.frameworkscanner.model.RuleInfo;

import java.util.List;

/** A group of related best-practice rules, checked together. */
public interface RuleSet {

    /** @return the rules of this set, with their explanation and before/after example */
    List<RuleInfo> rules();

    /**
     * Checks the library and reports findings through the context.
     *
     * @param ctx the scanned code, settings and the finding sink
     */
    void check(RuleContext ctx);
}
