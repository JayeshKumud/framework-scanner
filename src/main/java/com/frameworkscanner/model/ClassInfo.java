package com.frameworkscanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One class (or interface, enum, record) of the library, with its methods. */
public class ClassInfo {

    public String packageName;
    public String name;
    public String qualifiedName;
    /** class, interface, enum or record. */
    public String kind;
    /** What the class is for in the framework, for example "Page object" or "Step definitions". */
    public String role;
    public String file;
    public int beginLine;
    public String javadoc;
    public String extendsType;
    public List<MethodInfo> methods = new ArrayList<>();

    /**
     * String values of fields and of locators assigned to fields, by field name, so actions on
     * {@code loginButton.click()} can name the selector. Filled by the parser.
     */
    @JsonIgnore
    public transient Map<String, String> fieldValues = new LinkedHashMap<>();

    /** Names of fields that hold a Playwright Locator. */
    @JsonIgnore
    public transient Set<String> locatorFields = new HashSet<>();

    @JsonIgnore
    public transient TypeDeclaration<?> node;

    @JsonIgnore
    public transient CompilationUnit unit;
}
