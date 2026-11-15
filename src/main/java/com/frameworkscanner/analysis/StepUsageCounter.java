package com.frameworkscanner.analysis;

import com.frameworkscanner.model.MethodInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Counts how many feature file steps each Cucumber step definition matches, so unused step definitions show
 * up in the report. Supports Cucumber expressions ({@code I log in as {string}}, optional text
 * {@code item(s)}, alternatives {@code click/press}) and regular expressions ({@code ^I log in as "(.*)"$}).
 */
public final class StepUsageCounter {

    private static final Pattern STEP_LINE = Pattern.compile("^\\s*(Given|When|Then|And|But|\\*)\\s+(.*)$");

    private StepUsageCounter() {}

    /**
     * Sets {@link MethodInfo#stepUsageCount} of every step definition.
     *
     * @param methods      the library methods (others are left alone)
     * @param featureFiles the .feature files
     */
    public static void count(List<MethodInfo> methods, List<Path> featureFiles) {
        List<String> steps = new ArrayList<>();
        for (Path file : featureFiles) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    var m = STEP_LINE.matcher(line);
                    if (m.matches()) steps.add(m.group(2).trim());
                }
            } catch (IOException e) {
                // unreadable feature file: its steps are not counted
            }
        }
        for (MethodInfo m : methods) {
            if (!m.isStep()) continue;
            Pattern p = toPattern(m.stepText);
            if (p == null) continue;
            m.stepUsageCount = (int) steps.stream().filter(s -> p.matcher(s).matches()).count();
        }
    }

    /**
     * Turns a step annotation into a regular expression.
     *
     * @param text the annotation value
     * @return the pattern, or null if it is not a valid regular expression
     */
    static Pattern toPattern(String text) {
        try {
            if (text.startsWith("^") || text.endsWith("$")) {
                String regex = text.replaceFirst("^\\^", "").replaceFirst("\\$$", "");
                return Pattern.compile(regex);
            }
            return Pattern.compile(cucumberExpression(text));
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static String cucumberExpression(String text) {
        List<String> parts = new ArrayList<>();
        for (String word : text.trim().split("\\s+")) {
            if (word.contains("/") && !word.contains("{")) {
                List<String> alternatives = new ArrayList<>();
                for (String alt : word.split("/")) alternatives.add(word(alt));
                parts.add("(?:" + String.join("|", alternatives) + ")");
            } else {
                parts.add(word(word));
            }
        }
        return String.join("\\s+", parts);
    }

    private static String word(String word) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < word.length()) {
            char c = word.charAt(i);
            if (c == '\\' && i + 1 < word.length()) {
                out.append(Pattern.quote(String.valueOf(word.charAt(i + 1))));
                i += 2;
            } else if (c == '{') {
                int end = word.indexOf('}', i);
                if (end < 0) end = word.length() - 1;
                out.append(parameter(word.substring(i + 1, end)));
                i = end + 1;
            } else if (c == '(') {
                int end = word.indexOf(')', i);
                if (end < 0) end = word.length() - 1;
                out.append("(?:").append(Pattern.quote(word.substring(i + 1, end))).append(")?");
                i = end + 1;
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return out.toString();
    }

    private static String parameter(String type) {
        return switch (type) {
            case "string" -> "(?:\"[^\"]*\"|'[^']*')";
            case "int", "long", "short", "byte", "biginteger" -> "-?\\d+";
            case "float", "double", "bigdecimal" -> "-?\\d*[.,]?\\d+";
            case "word" -> "\\S+";
            default -> ".*";
        };
    }
}
