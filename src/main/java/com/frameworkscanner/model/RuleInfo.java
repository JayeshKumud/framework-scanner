package com.frameworkscanner.model;

/**
 * A best-practice rule: what it checks, why it matters and how to fix it.
 *
 * @param id       stable id, used in scanner.yaml to switch the rule off
 * @param title    short name
 * @param category for example "Naming", "Synchronisation", "Design"
 * @param severity how important a finding is
 * @param why      why the rule matters
 * @param before   code example that breaks the rule
 * @param after    the same code, fixed
 */
public record RuleInfo(String id, String title, String category, Severity severity, String why, String before,
                       String after) {}
