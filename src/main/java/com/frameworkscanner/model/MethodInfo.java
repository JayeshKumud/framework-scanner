package com.frameworkscanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * One method of the library: where it is, what it looks like, what it does, and how often it is used.
 * Filled step by step: the parser sets the location and signature, the analysis adds actions and the
 * description, the usage counter adds the call count and git blame adds the authors.
 */
public class MethodInfo {

    /** Unique id, {@code com.x.LoginPage#login(String,String)}. */
    public String id;
    public String packageName;
    public String className;
    public String qualifiedClassName;
    public String name;
    /** For example {@code public void login(String user, String password)}. */
    public String signature;
    public String returnType;
    public List<String> parameterTypes = new ArrayList<>();
    public List<String> parameterNames = new ArrayList<>();
    public List<String> annotations = new ArrayList<>();
    public String visibility;
    public boolean isStatic;
    /** Path relative to project.root, with forward slashes. */
    public String file;
    public int beginLine;
    public int endLine;
    public int statementCount;
    public int tokenCount;
    /** The original source of the method, as written. */
    public String source;
    /** First sentence of the Javadoc, or null. */
    public String javadoc;
    /** Cucumber step text if the method is a step definition, for example {@code I log in as {string}}. */
    public String stepKeyword;
    public String stepText;

    /** What the method does, in order. */
    public List<Action> actions = new ArrayList<>();
    /** What the method does, in one sentence. */
    public String description;
    /** Where the description came from: JAVADOC, STEP, CODE or NAME. */
    public String descriptionSource;
    /** Purpose category, for example "UI action", "API request", "Assertion", "Test data". */
    public String purpose;

    /** How often it is called in usages.sourceDirs (by name and argument count, so approximate). */
    public int callCount;
    /** For step definitions: how many feature file lines use the step. -1 if not counted. */
    public int stepUsageCount = -1;
    /** Authors of its lines, most lines first (git blame). */
    public List<String> authors = new ArrayList<>();

    /** The parsed node, for rules and duplicate detection; not written to reports. */
    @JsonIgnore
    public transient MethodDeclaration node;
    /** Code tokens with comments and whitespace removed (exact copies). */
    @JsonIgnore
    public transient List<String> exactTokens = List.of();
    /** Code tokens with names and literals replaced (renamed copies). */
    @JsonIgnore
    public transient List<String> normalizedTokens = List.of();

    /** @return number of source lines */
    public int lines() {
        return endLine - beginLine + 1;
    }

    /** @return the main author, or null */
    public String author() {
        return authors.isEmpty() ? null : authors.get(0);
    }

    /** @return {@code LoginPage.login} */
    public String shortName() {
        return className + "." + name;
    }

    /** @return true if the method is a Cucumber step definition */
    public boolean isStep() {
        return stepText != null;
    }
}
