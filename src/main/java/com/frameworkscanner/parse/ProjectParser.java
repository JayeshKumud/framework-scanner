package com.frameworkscanner.parse;

import com.frameworkscanner.analysis.ActionExtractor;
import com.frameworkscanner.config.ScannerConfig;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.MethodInfo;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads the library's Java files into {@link ClassInfo} and {@link MethodInfo} objects. It only parses: no
 * compilation, no class path, so the framework does not have to build for the scan to work.
 */
public final class ProjectParser {

    /** Cucumber step annotations. */
    public static final Set<String> STEP_ANNOTATIONS = Set.of("Given", "When", "Then", "And", "But", "Step");

    private final ScannerConfig config;
    private final Path root;
    private final JavaParser parser;
    private final List<PathMatcher> excludes = new ArrayList<>();
    /** Parsed files by absolute path, reused by the usage counter. */
    private final Map<Path, CompilationUnit> units = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private int filesScanned;

    /**
     * Creates a parser for one config.
     *
     * @param config the scanner config
     */
    public ProjectParser(ScannerConfig config) {
        this.config = config;
        this.root = config.projectRoot();
        ParserConfiguration pc = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setCharacterEncoding(StandardCharsets.UTF_8);
        this.parser = new JavaParser(pc);
        for (String glob : config.library.excludePaths) {
            excludes.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
    }

    /**
     * Parses every library file that passes the package and path filters.
     *
     * @return the classes, in file order
     */
    public List<ClassInfo> parseLibrary() {
        List<ClassInfo> classes = new ArrayList<>();
        for (Path file : javaFiles(config.librarySourceDirs())) {
            if (isExcludedPath(file)) continue;
            CompilationUnit unit = parse(file);
            if (unit == null) continue;
            String pkg = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            if (!packageIncluded(pkg)) continue;
            filesScanned++;
            List<String> lines = readLines(file);
            for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
                classes.add(readClass(unit, type, pkg, file, lines));
            }
        }
        return classes;
    }

    /**
     * Parses a file, or returns the cached result.
     *
     * @param file the file
     * @return the parsed unit, or null if it has syntax errors (recorded in {@link #errors()})
     */
    public CompilationUnit parse(Path file) {
        Path key = file.toAbsolutePath().normalize();
        if (units.containsKey(key)) return units.get(key);
        CompilationUnit unit = null;
        try {
            ParseResult<CompilationUnit> result = parser.parse(key);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                unit = result.getResult().get();
            } else {
                errors.add(relative(key) + ": " + result.getProblems().stream().findFirst()
                        .map(p -> p.getMessage().lines().findFirst().orElse("")).orElse("parse error"));
            }
        } catch (IOException e) {
            errors.add(relative(key) + ": " + e.getMessage());
        }
        units.put(key, unit);
        return unit;
    }

    /**
     * All .java files under some folders.
     *
     * @param dirs the folders (missing ones are skipped)
     * @return the files, sorted
     */
    public static List<Path> javaFiles(List<Path> dirs) {
        return files(dirs, ".java");
    }

    /**
     * All files with an extension under some folders.
     *
     * @param dirs      the folders (missing ones are skipped)
     * @param extension for example ".feature"
     * @return the files, sorted, without duplicates
     */
    public static List<Path> files(List<Path> dirs, String extension) {
        List<Path> out = new ArrayList<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(extension))
                        .map(p -> p.toAbsolutePath().normalize()).forEach(out::add);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("Cannot list " + dir, e);
            }
        }
        return out.stream().distinct().sorted().toList();
    }

    /** @return problems with files that could not be parsed */
    public List<String> errors() {
        return errors;
    }

    /** @return number of library files scanned (after filters) */
    public int filesScanned() {
        return filesScanned;
    }

    /**
     * A path relative to project.root, with forward slashes.
     *
     * @param file an absolute path
     * @return the relative path
     */
    public String relative(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        Path rel = abs.startsWith(root) ? root.relativize(abs) : abs;
        return rel.toString().replace('\\', '/');
    }

    private boolean isExcludedPath(Path file) {
        Path rel = Path.of(relative(file));
        return excludes.stream().anyMatch(m -> m.matches(rel));
    }

    private boolean packageIncluded(String pkg) {
        if (config.library.excludePackages.stream().anyMatch(p -> inPackage(pkg, p))) return false;
        return config.library.includePackages.isEmpty()
                || config.library.includePackages.stream().anyMatch(p -> inPackage(pkg, p));
    }

    private static boolean inPackage(String pkg, String prefix) {
        String p = prefix.trim();
        return pkg.equals(p) || pkg.startsWith(p + ".");
    }

    private ClassInfo readClass(CompilationUnit unit, TypeDeclaration<?> type, String pkg, Path file,
                                List<String> lines) {
        ClassInfo c = new ClassInfo();
        c.node = type;
        c.unit = unit;
        c.packageName = pkg;
        c.name = type.getNameAsString();
        c.qualifiedName = type.getFullyQualifiedName().orElse(pkg.isEmpty() ? c.name : pkg + "." + c.name);
        c.kind = type instanceof EnumDeclaration ? "enum"
                : type instanceof RecordDeclaration ? "record"
                : type instanceof ClassOrInterfaceDeclaration ci && ci.isInterface() ? "interface"
                : type.isAnnotationDeclaration() ? "annotation" : "class";
        c.file = relative(file);
        c.beginLine = type.getBegin().map(p -> p.line).orElse(0);
        c.javadoc = type.getJavadocComment().map(ProjectParser::summary).orElse(null);
        if (type instanceof ClassOrInterfaceDeclaration ci && !ci.getExtendedTypes().isEmpty()) {
            c.extendsType = ci.getExtendedTypes().get(0).getNameAsString();
        }
        readFields(c, type);
        for (MethodDeclaration m : type.getMethods()) {
            if (!config.library.includePrivateMethods && m.isPrivate()) continue;
            c.methods.add(readMethod(c, m, lines));
        }
        return c;
    }

    /** Remembers string values and locators of fields, so actions can name the element they act on. */
    private void readFields(ClassInfo c, TypeDeclaration<?> type) {
        for (FieldDeclaration field : type.getFields()) {
            for (VariableDeclarator v : field.getVariables()) {
                if (isLocatorType(v.getTypeAsString())) c.locatorFields.add(v.getNameAsString());
                v.getInitializer().ifPresent(init -> rememberField(c, v.getNameAsString(), init));
            }
        }
        // locators assigned in constructors or init methods: this.loginButton = page.locator("#login")
        for (AssignExpr assign : type.findAll(AssignExpr.class)) {
            if (assign.getOperator() != AssignExpr.Operator.ASSIGN) continue;
            Expression target = assign.getTarget();
            String name = target instanceof NameExpr n ? n.getNameAsString()
                    : target instanceof FieldAccessExpr f && f.getScope().isThisExpr() ? f.getNameAsString() : null;
            if (name == null || !isField(type, name)) continue;
            rememberField(c, name, assign.getValue());
        }
    }

    private static void rememberField(ClassInfo c, String name, Expression init) {
        if (init instanceof StringLiteralExpr s) {
            c.fieldValues.putIfAbsent(name, s.asString());
        } else if (ActionExtractor.isLocatorFactoryCall(init)) {
            c.locatorFields.add(name);
            String target = ActionExtractor.describeStatic(init, c);
            if (target != null) c.fieldValues.putIfAbsent(name, target);
        }
    }

    private static boolean isField(TypeDeclaration<?> type, String name) {
        return type.getFieldByName(name).isPresent();
    }

    /**
     * Tells whether a type name is Playwright's Locator.
     *
     * @param type a type as written
     * @return true for Locator
     */
    public static boolean isLocatorType(String type) {
        return type.equals("Locator") || type.endsWith(".Locator");
    }

    private MethodInfo readMethod(ClassInfo c, MethodDeclaration m, List<String> lines) {
        MethodInfo info = new MethodInfo();
        info.node = m;
        info.packageName = c.packageName;
        info.className = c.name;
        info.qualifiedClassName = c.qualifiedName;
        info.name = m.getNameAsString();
        for (Parameter p : m.getParameters()) {
            info.parameterTypes.add(p.getTypeAsString() + (p.isVarArgs() ? "..." : ""));
            info.parameterNames.add(p.getNameAsString());
        }
        info.id = c.qualifiedName + "#" + info.name + "(" + String.join(",", info.parameterTypes) + ")";
        info.returnType = m.getTypeAsString();
        info.signature = m.getDeclarationAsString(true, false, true);
        info.visibility = m.isPublic() ? "public" : m.isProtected() ? "protected" : m.isPrivate() ? "private"
                : "package";
        info.isStatic = m.isStatic();
        for (AnnotationExpr a : m.getAnnotations()) {
            info.annotations.add(a.getNameAsString());
            String simple = a.getName().getIdentifier();
            if (STEP_ANNOTATIONS.contains(simple) && info.stepText == null) {
                info.stepKeyword = simple;
                info.stepText = annotationValue(a);
            }
        }
        info.file = c.file;
        info.beginLine = m.getBegin().map(p -> p.line).orElse(0);
        info.endLine = m.getEnd().map(p -> p.line).orElse(info.beginLine);
        info.javadoc = m.getJavadocComment().map(ProjectParser::summary).orElse(null);
        info.statementCount = m.getBody().map(ProjectParser::countStatements).orElse(0);
        info.source = slice(lines, m);
        return info;
    }

    private static int countStatements(BlockStmt body) {
        return (int) body.findAll(Statement.class).stream().filter(s -> !s.isBlockStmt()).count();
    }

    private static String annotationValue(AnnotationExpr a) {
        Optional<Expression> value = Optional.empty();
        if (a instanceof SingleMemberAnnotationExpr s) value = Optional.of(s.getMemberValue());
        if (a instanceof NormalAnnotationExpr n) {
            value = n.getPairs().stream().filter(p -> p.getNameAsString().equals("value")).findFirst()
                    .map(p -> p.getValue());
        }
        return value.map(v -> v instanceof StringLiteralExpr s ? s.asString() : v.toString()).orElse("");
    }

    /**
     * The first sentence of a Javadoc comment, without tags and HTML.
     *
     * @param doc the comment
     * @return the sentence, or null if the comment has no description
     */
    static String summary(JavadocComment doc) {
        String text = doc.parse().getDescription().toText()
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\{@\\w+\\s+([^}]*)}", "$1")
                .replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return null;
        int dot = text.indexOf(". ");
        return dot > 0 ? text.substring(0, dot + 1) : text;
    }

    private static String slice(List<String> lines, Node node) {
        int begin = node.getBegin().map(p -> p.line).orElse(1);
        int end = node.getEnd().map(p -> p.line).orElse(begin);
        if (lines.isEmpty() || begin > lines.size()) return node.toString();
        // include annotations and Javadoc? Only the declaration itself: annotations are part of the node range.
        List<String> part = lines.subList(Math.max(0, begin - 1), Math.min(lines.size(), end));
        int indent = part.stream().filter(l -> !l.isBlank()).mapToInt(ProjectParser::indentOf).min().orElse(0);
        StringBuilder out = new StringBuilder();
        for (String line : part) out.append(line.length() >= indent ? line.substring(indent) : line.strip()).append('\n');
        return out.toString().stripTrailing();
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return i;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            } catch (IOException ex) {
                return List.of();
            }
        }
    }

    /**
     * Tells whether a type is a Playwright or REST Assured type that must not be shared between threads.
     *
     * @param type the type
     * @return true for Page, Browser, RequestSpecification, ...
     */
    public static boolean isFrameworkStateType(ClassOrInterfaceType type) {
        return Set.of("Page", "Browser", "BrowserContext", "Playwright", "APIRequestContext", "Locator",
                "RequestSpecification", "Response", "ValidatableResponse", "WebDriver").contains(type.getNameAsString());
    }

    /** @return the modifiers keyword set, for rules */
    static boolean hasModifier(FieldDeclaration f, Modifier.Keyword k) {
        return f.getModifiers().stream().anyMatch(m -> m.getKeyword() == k);
    }
}
