package com.frameworkscanner.rules;

import com.frameworkscanner.config.ScannerConfig;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.Finding;
import com.frameworkscanner.model.RuleInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs every rule set and collects the findings. */
public final class RuleEngine {

    private final List<RuleSet> sets = List.of(new NamingRules(), new SynchronisationRules(), new DesignRules(),
            new TestDesignRules(), new RestAssuredRules(), new ErrorHandlingRules(), new JavaIdiomRules());

    /** @return every rule, enabled or not, in report order */
    public List<RuleInfo> rules() {
        List<RuleInfo> all = new ArrayList<>();
        sets.forEach(s -> all.addAll(s.rules()));
        return all;
    }

    /**
     * Checks the library.
     *
     * @param classes  the library classes, analysed
     * @param settings the rules section of scanner.yaml
     * @return the findings, most severe first, then by file and line
     */
    public List<Finding> check(List<ClassInfo> classes, ScannerConfig.Rules settings) {
        Map<String, RuleInfo> byId = new LinkedHashMap<>();
        rules().forEach(r -> byId.put(r.id(), r));
        RuleContext ctx = new RuleContext(classes, settings, byId);
        for (RuleSet set : sets) set.check(ctx);
        List<Finding> out = new ArrayList<>(ctx.findings());
        out.sort(Comparator.comparing(Finding::severity).thenComparing(Finding::file).thenComparingInt(Finding::line));
        return out;
    }
}
